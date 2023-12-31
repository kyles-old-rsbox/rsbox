package io.rsbox.server.engine.model.entity

import io.rsbox.server.common.inject
import io.rsbox.server.engine.Engine
import io.rsbox.server.engine.model.Tile
import io.rsbox.server.engine.model.World

abstract class Entity {

    val engine: Engine by inject()
    val world: World by inject()

    abstract val sizeX: Int
    abstract val sizeY: Int

    abstract var tile: Tile
    abstract var prevTile: Tile

    var index: Int = -1
    var invisible = false

    abstract suspend fun cycle()

}