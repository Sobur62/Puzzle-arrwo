package com.example.game.engine

import com.example.game.model.GameMode
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrowLevelGeneratorTest {

  @Test
  fun classicLevels_1to50_areCompletelySolvableWithNoOverlaps() {
    for (lvl in listOf(1, 2, 3, 5, 10, 20, 35, 50)) {
      val puzzle = ArrowLevelGenerator.generateLevel(GameMode.CLASSIC, lvl)
      assertTrue("Level $lvl should have arrows", puzzle.arrows.isNotEmpty())

      // Verify no cell overlaps
      val allCells = puzzle.arrows.flatMap { it.cells }
      assertTrue(
        "Level $lvl has overlapping cells",
        allCells.size == allCells.toSet().size,
      )

      // Greedy forward peel must clear 100% of arrows
      val remaining = puzzle.arrows.toMutableList()
      while (remaining.isNotEmpty()) {
        val free =
          remaining.firstOrNull {
            ArrowLevelGenerator.isArrowFree(it, remaining, puzzle.cols, puzzle.rows)
          }
        assertTrue("Level $lvl deadlocked with ${remaining.size} arrows left", free != null)
        remaining.remove(free)
      }
    }
  }

  @Test
  fun fallStages_1to15_areSolvableWithGravity() {
    for (stage in listOf(1, 2, 3, 5, 8, 12, 15)) {
      val puzzle = ArrowLevelGenerator.generateLevel(GameMode.FALL, stage)
      assertTrue("Fall stage $stage should have arrows", puzzle.arrows.isNotEmpty())
      assertTrue(
        "Fall stage $stage must be solvable with gravity",
        ArrowLevelGenerator.isSolvableWithGravity(puzzle.cols, puzzle.rows, puzzle.arrows, maxStates = 1500),
      )
    }
  }
}
