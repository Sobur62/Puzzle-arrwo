package com.example.game.engine

// Backward-compatible facade delegating to the default ProceduralLevelGenerator service
object ArrowLevelGenerator : LevelGenerator by ProceduralLevelGenerator()
