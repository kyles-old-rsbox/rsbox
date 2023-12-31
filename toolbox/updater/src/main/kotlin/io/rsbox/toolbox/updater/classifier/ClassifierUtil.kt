package io.rsbox.toolbox.updater.classifier

import io.rsbox.toolbox.updater.Updater
import io.rsbox.toolbox.updater.asm.ClassEntry
import io.rsbox.toolbox.updater.asm.FieldEntry
import io.rsbox.toolbox.updater.asm.Matchable
import io.rsbox.toolbox.updater.asm.MethodEntry
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.AbstractInsnNode.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ClassifierUtil {

    fun isPotentiallyEqual(a: ClassEntry, b: ClassEntry): Boolean {
        if(a == b) return true
        if(a.hasMatch()) return a.match == b
        if(b.hasMatch()) return b.match == a
        if(a.isArray() != b.isArray()) return false
        if(a.isArray() && !isPotentiallyEqual(a.elementClass!!, b.elementClass!!)) return false
        return isMaybeEqual(a, b)
    }

    fun isPotentiallyEqual(a: MethodEntry, b: MethodEntry): Boolean {
        if(a == b) return true
        if(a.hasMatch()) return a.match == b
        if(b.hasMatch()) return b.match == a
        if(!a.isStatic() && !b.isStatic()) {
            if(!isPotentiallyEqual(a.cls, b.cls)) return false
        }
        if((a.name.startsWith("<") ||  b.name.startsWith("<")) && a.name != b.name) return false
        return isMaybeEqual(a, b)
    }

    fun isPotentiallyEqual(a: FieldEntry, b: FieldEntry): Boolean {
        if(a == b) return true
        if(a.hasMatch()) return a.match == b
        if(b.hasMatch()) return b.match == a
        if(!a.isStatic() && !b.isStatic()) {
            if(!isPotentiallyEqual(a.cls, b.cls)) return false
        }
        return isMaybeEqual(a, b)
    }

    fun isPotentiallyEqualNullable(a: ClassEntry?, b: ClassEntry?): Boolean {
        if(a == null && b == null) return true
        if(a == null || b == null) return false
        return isPotentiallyEqual(a, b)
    }

    fun isPotentiallyEqualNullable(a: MethodEntry?, b: MethodEntry?): Boolean {
        if(a == null && b == null) return true
        if(a == null || b == null) return false
        return isPotentiallyEqual(a, b)
    }

    fun isPotentiallyEqualNullable(a: FieldEntry?, b: FieldEntry?): Boolean {
        if(a == null && b == null) return true
        if(a == null || b == null) return false
        return isPotentiallyEqual(a, b)
    }

    fun isMaybeEqual(a: Type, b: Type): Boolean {
        if(a.dimensions != b.dimensions) return false

        var ta = a
        var tb = b

        while(ta.dimensions > 0) {
            ta = ta.elementType
            tb = tb.elementType
        }

        if(ta.isPrimitive() || tb.isPrimitive()) {
            return ta == tb
        }

        return true
    }

    fun isMaybeEqual(a: MethodEntry, b: MethodEntry): Boolean {
        val sa = a.type
        val sb = b.type

        if(sa.size != sb.size) return false
        if(!isMaybeEqual(sa.returnType, sb.returnType)) return false

        for(i in 0 until sa.size) {
            val ta = sa.argumentTypes[i]
            val tb = sb.argumentTypes[i]
            if(!isMaybeEqual(ta, tb)) return false
        }

        return true
    }

    fun isMaybeEqual(a: FieldEntry?, b: FieldEntry?): Boolean {
        if(a == null && b == null) return true
        if(a == null || b == null) return false
        if(a.isStatic() != b.isStatic()) return false
        return isMaybeEqual(a.type, b.type)
    }

    fun isMaybeEqual(a: ClassEntry?, b: ClassEntry?): Boolean {
        if(a == null && b == null) return true
        if(a == null || b == null) return false
        if(!isMaybeEqual(a.superClass, b.superClass)) return false
        return a.interfaces.size == b.interfaces.size
    }

    fun <T : Matchable<T>> rank(src: T, dsts: Array<T>, analyzers: Collection<Analyzer<T>>, potentiallyEqualCheck: (T, T) -> Boolean, maxMismatch: Double): List<RankResult<T>> {
        val results = mutableListOf<RankResult<T>>()

        for(dst in dsts) {
            val result = rank(src, dst, analyzers, potentiallyEqualCheck, maxMismatch)
            if(result != null) results.add(result)
        }

        results.sortWith(Comparator.comparing(RankResult<T>::score).reversed())

        return results
    }

    private fun <T : Matchable<T>> rank(src: T, dst: T, analyzers: Collection<Analyzer<T>>, potentiallyEqualCheck: (T, T) -> Boolean, maxMismatch: Double): RankResult<T>? {
        if(!potentiallyEqualCheck(src, dst)) return null

        var score = 0.0
        var mismatch = 0.0
        val results = ArrayList<AnalyzerResult<T>>(analyzers.size)

        for(analyzer in analyzers) {
            val cscore = analyzer.getScore(src, dst)

            val weight = analyzer.weight
            val weightedScore = cscore * weight

            mismatch += weight - weightedScore
            if(mismatch >= maxMismatch) return null

            score += weightedScore
            results.add(AnalyzerResult(analyzer, cscore))
        }

        return RankResult(dst, score, results)
    }

    fun compareCounts(a: Int, b: Int): Double {
        val delta = abs(a - b)
        if(delta == 0) return 1.0
        return 1 - delta.toDouble() / max(a, b)
    }

    fun <T> compareSets(a: HashSet<T>, b: HashSet<T>): Double {
        val setA = hashSetOf<T>().also { it.addAll(a) }
        val setB = hashSetOf<T>().also { it.addAll(b) }

        val oldSize = setB.size
        setB.removeAll(setA)

        val matched = oldSize - setB.size
        val total = setA.size - matched + oldSize

        return if (total == 0) 1.0 else matched.toDouble() / total
    }

    fun compareClassSets(a: HashSet<ClassEntry>, b: HashSet<ClassEntry>): Double {
        return compareMatchableSets(a, b, ClassifierUtil::isPotentiallyEqual)
    }

    fun compareMethodSets(a: HashSet<MethodEntry>, b: HashSet<MethodEntry>): Double {
        return compareMatchableSets(a, b, ClassifierUtil::isPotentiallyEqual)
    }

    fun compareFieldSets(a: HashSet<FieldEntry>, b: HashSet<FieldEntry>): Double {
        return compareMatchableSets(a, b, ClassifierUtil::isPotentiallyEqual)
    }

    private fun <T : Matchable<T>> compareMatchableSets(a: HashSet<T>, b: HashSet<T>, compare: (a: T, b: T) -> Boolean): Double {
        if(a.isEmpty() || b.isEmpty()) {
            return if(a.isEmpty() && b.isEmpty()) 1.0 else 0.0
        }

        val setA = hashSetOf<T>().also { it.addAll(a) }
        val setB = hashSetOf<T>().also { it.addAll(b) }

        val total = setA.size + setB.size
        var unmatched = 0

        val itr1 = setA.iterator()
        while(itr1.hasNext()) {
            val entryA = itr1.next()
            if(setB.remove(entryA)) {
                itr1.remove()
            } else if(entryA.hasMatch()) {
                if(!setB.remove(entryA.match)) {
                    unmatched++
                }
                itr1.remove()
            }
        }

        val itr2 = setA.iterator()
        while(itr2.hasNext()) {
            val entryA = itr2.next()
            var found = false
            for(entryB in setB) {
                if(compare(entryA, entryB)) {
                    found = true
                    break
                }
            }
            if(!found) {
                unmatched++
                itr2.remove()
            }
        }

        for(entryB in setB) {
            var found = false
            for(entryA in setA) {
                if(compare(entryA, entryB)) {
                    found = true
                    break
                }
            }
            if(!found) {
                unmatched++
            }
        }

        return (total - unmatched).toDouble() / total
    }

    fun compareClassLists(listA: List<ClassEntry>, listB: List<ClassEntry>): Double {
        return compareLists(listA, listB, List<ClassEntry>::get, List<ClassEntry>::size) { a, b ->
            if (isPotentiallyEqual(a, b)) COMPARED_SIMILAR else COMPARED_DISTINCT
        }
    }

    fun compareFieldLists(listA: List<FieldEntry>, listB: List<FieldEntry>): Double {
        return compareLists(listA, listB, List<FieldEntry>::get, List<FieldEntry>::size) { a, b ->
            if(isPotentiallyEqual(a, b)) COMPARED_SIMILAR else COMPARED_DISTINCT
        }
    }

    private fun <T, U> compareLists(listA: T, listB: T, getElement: (T, Int) -> U, getSize: (T) -> Int, compareElement: (a: U, b: U) -> Int): Double {
        val sizeA = getSize(listA)
        val sizeB = getSize(listB)

        if(sizeA == 0 && sizeB == 0) return 1.0
        if(sizeA == 0 || sizeB == 0) return 0.0

        if(sizeA == sizeB) {
            var match = true
            for(i in 0 until sizeA) {
                if(compareElement(getElement(listA, i), getElement(listB, i)) != COMPARED_SIMILAR) {
                    match = false
                    break
                }
            }

            if(match) return 1.0
        }

        val v0 = IntArray(sizeB + 1)
        val v1 = IntArray(sizeB + 1)

        for(i in 1 until v0.size) {
            v0[i] = i * COMPARED_DISTINCT
        }

        for(i in 0 until sizeA) {
            v1[0] = (i + 1) * COMPARED_DISTINCT

            for(j in 0 until sizeB) {
                val cost = compareElement(getElement(listA, i), getElement(listB, j))
                v1[j + 1] = min(min(v1[j] + COMPARED_DISTINCT, v0[j + 1] + COMPARED_DISTINCT), v0[j] + cost)
            }

            for(j in v0.indices) {
                v0[j] = v1[j]
            }
        }

        val distance = v1[sizeB]
        val upperBound = max(sizeA, sizeB) * COMPARED_DISTINCT

        return 1 - distance.toDouble() / upperBound
    }

    private fun <T, U> mapLists(listA: T, listB: T, getElement: (T, Int) -> U, getSize: (T) -> Int, compareElement: (a: U, b: U) -> Int): IntArray {
        val sizeA = getSize(listA)
        val sizeB = getSize(listB)

        if(sizeA == 0 && sizeB == 0) return intArrayOf()

        val ret = IntArray(sizeA)

        if(sizeA == 0 || sizeB == 0) {
            Arrays.fill(ret, -1)
            return ret
        }

        if(sizeA == sizeB) {
            var match = true
            for(i in 0 until sizeA) {
                if(compareElement(getElement(listA, i), getElement(listB, i)) != COMPARED_SIMILAR) {
                    match = false
                    break
                }
            }

            if(match) {
                for(i in ret.indices) {
                    ret[i] = i
                }
                return ret
            }
        }

        val size = sizeA + 1
        val v = IntArray(size * (sizeB + 1))

        for(i in 1 .. sizeA) {
            v[i + 0] = i * COMPARED_DISTINCT
        }

        for(j in 1 .. sizeB) {
            v[0 + j * size] = j * COMPARED_DISTINCT
        }

        for(j in 1 .. sizeB) {
            for(i in 1 .. sizeA) {
                val cost = compareElement(getElement(listA, i - 1), getElement(listB, j - 1))
                v[i + j * size] = min(min(v[i - 1 + j * size] + COMPARED_DISTINCT,
                    v[i + (j - 1) * size] + COMPARED_DISTINCT),
                    v[i - 1 + (j - 1) * size] + cost)
            }
        }

        var i = sizeA
        var j = sizeB

        while(i > 0 || j > 0) {
            val c = v[i + j * size]
            val delCost = if(i > 0) v[i - 1 + j * size] else Int.MAX_VALUE
            val insCost = if(j > 0) v[i + (j - 1) * size] else Int.MAX_VALUE
            val keepCost = if(j > 0 && i > 0) v[i - 1 + (j - 1) * size] else Int.MAX_VALUE

            if(keepCost <= delCost && keepCost <= insCost) {
                if(c - keepCost >= COMPARED_DISTINCT) {
                    ret[i - 1] = -1
                } else {
                    ret[i - 1] = j - 1
                }
                i--
                j--
            } else if(delCost < insCost) {
                ret[i - 1] = -1
                i--
            } else {
                j--
            }
        }

        return ret
    }

    fun compareInsns(listA: List<AbstractInsnNode>, listB: List<AbstractInsnNode>): Double {
        return compareLists(listA, listB, List<AbstractInsnNode>::get, List<AbstractInsnNode>::size) { insnA, insnB ->
            compareInsns(insnA, insnB, listA, listB, { list, insn -> list.indexOf(insn) }, null, null)
        }
    }

    fun compareInsns(listA: InsnList, listB: InsnList, mthA: MethodEntry? = null, mthB: MethodEntry? = null): Double {
        return compareLists(listA, listB, InsnList::get, InsnList::size) { insnA, insnB ->
            compareInsns(insnA, insnB, listA, listB, { list, insn -> list.indexOf(insn) }, mthA, mthB)
        }
    }

    private fun <T> compareInsns(insnA: AbstractInsnNode, insnB: AbstractInsnNode, listA: T, listB: T, getPos: (T, AbstractInsnNode) -> Int, mthA: MethodEntry?, mthB: MethodEntry?): Int {
        if(insnA.opcode != insnB.opcode) return COMPARED_DISTINCT
        val env = Updater.env

        when(insnA.type) {
            INT_INSN -> {
                insnA as IntInsnNode
                insnB as IntInsnNode

                return if(insnA.operand == insnB.operand) COMPARED_SIMILAR else COMPARED_DISTINCT
            }

            TYPE_INSN -> {
                insnA as TypeInsnNode
                insnB as TypeInsnNode

                val clsA = env.groupA.getClass(insnA.desc)
                val clsB = env.groupB.getClass(insnB.desc)

                return if(isPotentiallyEqualNullable(clsA, clsB)) COMPARED_SIMILAR else COMPARED_DISTINCT
            }

            FIELD_INSN -> {
                insnA as FieldInsnNode
                insnB as FieldInsnNode

                val ownerA = env.groupA.getClass(insnA.owner)
                val ownerB = env.groupB.getClass(insnB.owner)

                if(ownerA == null && ownerB == null) return COMPARED_SIMILAR
                if(ownerA == null || ownerB == null) return COMPARED_DISTINCT

                val fieldA = ownerA.resolveField(insnA.name, insnB.desc)
                val fieldB = ownerB.resolveField(insnB.name, insnB.desc)

                return if(isPotentiallyEqualNullable(fieldA, fieldB)) COMPARED_SIMILAR else COMPARED_DISTINCT
            }

            METHOD_INSN -> {
                insnA as MethodInsnNode
                insnB as MethodInsnNode

                return if(compareMethods(
                        insnA.owner, insnA.name, insnA.desc, insnA.itf,
                        insnB.owner, insnB.name, insnB.desc, insnB.itf
                    )) COMPARED_SIMILAR else COMPARED_DISTINCT
            }

            JUMP_INSN -> {
                insnA as JumpInsnNode
                insnB as JumpInsnNode

                val dirA = Integer.signum(getPos(listA, insnA.label) - getPos(listA, insnA))
                val dirB = Integer.signum(getPos(listB, insnB.label) - getPos(listB, insnB))

                return if(dirA == dirB) COMPARED_SIMILAR else COMPARED_DISTINCT
            }

            LDC_INSN -> {
                insnA as LdcInsnNode
                insnB as LdcInsnNode

                val typeClsA = insnA.cst::class.java
                if(typeClsA != insnB.cst::class.java) return COMPARED_DISTINCT

                if(typeClsA == Type::class.java) {
                    val typeA = insnA.cst as Type
                    val typeB = insnB.cst as Type
                    if(typeA.sort != typeB.sort) return COMPARED_DISTINCT
                    when(typeA.sort) {
                        Type.ARRAY, Type.OBJECT -> {
                            return if(isPotentiallyEqualNullable(env.groupA.getClass(typeA.descriptor), env.groupB.getClassById(typeB.descriptor))) COMPARED_SIMILAR else COMPARED_DISTINCT
                        }
                        Type.METHOD -> { /* Not Implemented */ }

                    }
                } else {
                    return if(insnA.cst == insnB.cst) COMPARED_SIMILAR else COMPARED_DISTINCT
                }
            }

            IINC_INSN -> {
                insnA as IincInsnNode
                insnB as IincInsnNode

                return if(insnA.incr != insnB.incr) COMPARED_DISTINCT else COMPARED_SIMILAR
            }

            TABLESWITCH_INSN -> {
                insnA as TableSwitchInsnNode
                insnB as TableSwitchInsnNode

                return if(insnA.min == insnB.min && insnA.max == insnB.max) COMPARED_SIMILAR else COMPARED_DISTINCT
            }

            LOOKUPSWITCH_INSN -> {
                insnA as LookupSwitchInsnNode
                insnB as LookupSwitchInsnNode

                return if(insnA.keys == insnB.keys) return COMPARED_SIMILAR else COMPARED_DISTINCT
            }

            MULTIANEWARRAY_INSN -> {
                insnA as MultiANewArrayInsnNode
                insnB as MultiANewArrayInsnNode

                if(insnA.dims != insnB.dims) return COMPARED_DISTINCT

                val clsA = env.groupA.getClass(insnA.desc)
                val clsB = env.groupB.getClass(insnB.desc)

                return if(isPotentiallyEqualNullable(clsA, clsB)) COMPARED_SIMILAR else COMPARED_DISTINCT
            }
        }

        return COMPARED_SIMILAR
    }

    private fun compareMethods(ownerA: String, nameA: String, descA: String, toIfA: Boolean, ownerB: String, nameB: String, descB: String, toIfB: Boolean): Boolean {
        val env = Updater.env

        val clsA = env.groupA.getClass(ownerA)
        val clsB = env.groupB.getClass(ownerB)

        if(clsA == null && clsB == null) return true
        if(clsA == null || clsB == null) return false

        val methodA = clsA.resolveMethod(nameA, descA, toIfA)
        val methodB = clsB.resolveMethod(nameB, descB, toIfB)

        if(methodA == null && methodB == null) return true
        if(methodA == null || methodB == null) return false

        return isPotentiallyEqual(methodA, methodB)
    }

    fun mapInsns(a: MethodEntry, b: MethodEntry): IntArray? {
        val insnsA = a.instructions
        val insnsB = b.instructions

        if(a.instructions.size() == 0 || b.instructions.size() == 0) return null

        return if(insnsA.size() * insnsB.size() < 1000) {
            mapInsns(insnsA, insnsB, a, b)
        } else {
            MatchCache.compute(INSN_MAP_CACHE, a, b) { ma, mb -> mapInsns(ma.instructions, mb.instructions, ma, mb) }
        }
    }

    fun mapInsns(listA: InsnList, listB: InsnList, mthA: MethodEntry? = null, mthB: MethodEntry? = null): IntArray {
        return mapLists(listA, listB, InsnList::get, InsnList::size) { insnA, insnB ->
            compareInsns(insnA, insnB, listA, listB, { list, insn -> list.indexOf(insn) }, mthA, mthB)
        }
    }

    fun extractStrings(insns: InsnList, out: HashSet<String>) {
        extractStrings(insns.iterator(), out)
    }

    fun extractStrings(insns: List<AbstractInsnNode>, out: HashSet<String>) {
        extractStrings(insns.iterator(), out)
    }

    private fun extractStrings(insns: Iterator<AbstractInsnNode>, out: HashSet<String>) {
        while(insns.hasNext()) {
            val insn = insns.next()
            if(insn is LdcInsnNode) {
                if(insn.cst is String) {
                    out.add(insn.cst as String)
                }
            }
        }
    }

    fun extractNumbers(insns: InsnList, numbers: Numbers) {
        val it = insns.iterator()
        while(it.hasNext()) {
            val insn = it.next()
            if(insn is LdcInsnNode) {
                handleNumberValue(insn.cst, numbers)
            } else if(insn is IntInsnNode) {
                numbers.ints.add(insn.operand)
            }
        }
    }

    fun handleNumberValue(number: Any?, numbers: Numbers) {
        if(number == null) return
        when(number) {
            is Int -> numbers.ints.add(number)
            is Long -> numbers.longs.add(number)
            is Float -> numbers.floats.add(number)
            is Double -> numbers.doubles.add(number)
        }
    }

    data class Numbers(
        val ints: HashSet<Int> = hashSetOf(),
        val longs: HashSet<Long> = hashSetOf(),
        val floats: HashSet<Float> = hashSetOf(),
        val doubles: HashSet<Double> = hashSetOf()
    )

    const val COMPARED_SIMILAR = 0
    const val COMPARED_POSSIBLE = 1
    const val COMPARED_DISTINCT = 2

    private val INSN_MAP_CACHE = MatchCache.CacheToken<IntArray>()

    private fun Type.isPrimitive(): Boolean = this.sort <= 8

    @Suppress("UNCHECKED_CAST")
    object MatchCache {

        class CacheToken<T>
        data class CacheKey<T : Matchable<T>>(val token: CacheToken<*>, val a: T, val b: T)

        private val cache = ConcurrentHashMap<CacheKey<*>, Any>()

        fun clear() { cache.clear() }

        fun <T, U : Matchable<U>> get(token: CacheToken<T>, a: U, b: U): T {
            return cache[CacheKey(token, a, b)] as T
        }

        fun <T : Any, U : Matchable<U>> compute(token: CacheToken<T>, a: U, b: U, f: (U, U) -> T): T {
            return cache.computeIfAbsent(CacheKey(token, a, b)) { f(it.a as U, it.b as U) } as T
        }

    }
}

private typealias MethodSignature = Type