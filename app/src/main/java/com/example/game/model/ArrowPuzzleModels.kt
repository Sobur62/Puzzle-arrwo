package com.example.game.model

enum class Direction(val dx: Int, val dy: Int) {
  UP(0, -1),
  DOWN(0, 1),
  LEFT(-1, 0),
  RIGHT(1, 0),
}

data class GridPoint(val x: Int, val y: Int) {
  operator fun plus(dir: Direction): GridPoint = GridPoint(x + dir.dx, y + dir.dy)
}

data class ArrowEntity(
  val id: Int,
  val cells: List<GridPoint>,
  val direction: Direction,
  val colorVariant: Int = 0,
) {
  val head: GridPoint
    get() = cells.last()

  val tail: GridPoint
    get() = cells.first()
}

enum class GameMode {
  CLASSIC,
  FALL,
}

enum class ArrowSkin(
  val id: String,
  val displayName: String,
) {
  GREEN_DOTS("green_dots", "Emerald Dots"),
  CLASSIC_INK("classic_ink", "Classic Ink"),
  GOOGLY_NOODLES("googly_noodles", "Googly Noodles"),
  MATCHSTICKS("matchsticks", "Matchsticks"),
  CATERPILLARS("caterpillars", "Caterpillars"),
  PRISM_ARROWS("prism_arrows", "Prism Arrows"),
  REINDEER("reindeer", "Reindeer"),
  SANTA("santa", "Santa"),
  PINE_TREE("pine_tree", "Pine Tree");

  companion object {
    fun fromId(id: String): ArrowSkin = entries.firstOrNull { it.id == id } ?: GREEN_DOTS
  }
}

data class PuzzleLevel(
  val mode: GameMode,
  val levelNumber: Int,
  val cols: Int,
  val rows: Int,
  val arrows: List<ArrowEntity>,
  val layoutName: String = "Open Grid",
)
