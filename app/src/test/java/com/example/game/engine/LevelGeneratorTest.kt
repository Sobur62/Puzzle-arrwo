package com.example.game.engine

import com.example.game.model.ArrowEntity
import com.example.game.model.Direction
import com.example.game.model.GameMode
import com.example.game.model.GridPoint
import com.example.game.model.PuzzleLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelGeneratorTest {

  private val generator: LevelGenerator = ProceduralLevelGenerator()

  private fun countTurns(arrow: ArrowEntity): Int {
    if (arrow.cells.size < 3) return 0
    var turns = 0
    for (i in 2 until arrow.cells.size) {
      val dx1 = arrow.cells[i - 1].x - arrow.cells[i - 2].x
      val dy1 = arrow.cells[i - 1].y - arrow.cells[i - 2].y
      val dx2 = arrow.cells[i].x - arrow.cells[i - 1].x
      val dy2 = arrow.cells[i].y - arrow.cells[i - 1].y
      if (dx1 != dx2 || dy1 != dy2) turns++
    }
    return turns
  }

  @Test
  fun kruskalAndPrimAlgorithms_bothGenerateSolvableMazesAcrossAllDifficultyTiers() {
    for (tier in DifficultyTier.entries) {
      for (algo in MazeAlgorithm.entries) {
        val puzzle =
          generator.generate(
            GenerationRequest(
              mode = GameMode.CLASSIC,
              levelNumber = 10,
              difficulty = tier,
              algorithm = algo,
              seed = 424242L,
            )
          )
        assertEquals(tier.defaultDimensions.cols, puzzle.cols)
        assertEquals(tier.defaultDimensions.rows, puzzle.rows)
        assertTrue(puzzle.layoutName.contains(algo.displayName))

        val report = generator.verifySolvable(puzzle)
        assertTrue(
          "Tier ${tier.name} with ${algo.name} failed solvability: ${report.failureReason}",
          report.isSolvable,
        )
        assertEquals(puzzle.arrows.size, report.solutionOrder.size)
      }
    }
  }

  @Test
  fun customDimensionsAndDifficultyTiers_controlGridSizeAndPathComplexity() {
    val fixedDims = MazeDimensions(cols = 14, rows = 14)

    val easyPuzzle =
      generator.generate(
        GenerationRequest(
          mode = GameMode.CLASSIC,
          levelNumber = 5,
          difficulty = DifficultyTier.EASY,
          dimensions = fixedDims,
          algorithm = MazeAlgorithm.KRUSKAL,
          seed = 7777L,
        )
      )
    val hardPuzzle =
      generator.generate(
        GenerationRequest(
          mode = GameMode.CLASSIC,
          levelNumber = 5,
          difficulty = DifficultyTier.HARD,
          dimensions = fixedDims,
          algorithm = MazeAlgorithm.PRIM,
          seed = 7777L,
        )
      )

    assertEquals(14, easyPuzzle.cols)
    assertEquals(14, easyPuzzle.rows)
    assertEquals(14, hardPuzzle.cols)
    assertEquals(14, hardPuzzle.rows)

    assertTrue(generator.verifySolvable(easyPuzzle).isSolvable)
    assertTrue(generator.verifySolvable(hardPuzzle).isSolvable)

    val easyAvgLen = easyPuzzle.arrows.map { it.cells.size }.average()
    val hardAvgLen = hardPuzzle.arrows.map { it.cells.size }.average()
    val easyTotalTurns = easyPuzzle.arrows.sumOf(::countTurns)
    val hardTotalTurns = hardPuzzle.arrows.sumOf(::countTurns)

    assertTrue(
      "Hard tier ($hardAvgLen) should create longer paths than Easy tier ($easyAvgLen)",
      hardAvgLen > easyAvgLen,
    )
    assertTrue(
      "Hard tier ($hardTotalTurns turns) should have higher winding complexity than Easy tier ($easyTotalTurns turns)",
      hardTotalTurns > easyTotalTurns,
    )
  }

  @Test
  fun classicLevels_1to60_areSolvableAndHaveValidGeometry() {
    val layoutsSeen = HashSet<String>()
    for (lvl in 1..60) {
      val puzzle = generator.generateLevel(GameMode.CLASSIC, lvl)
      layoutsSeen.add(puzzle.layoutName)

      val report = generator.verifySolvable(puzzle)
      assertTrue(
        "Classic level $lvl (${puzzle.layoutName}) failed solvability: ${report.failureReason}",
        report.isSolvable,
      )
      assertEquals(puzzle.arrows.size, report.solutionOrder.size)
    }

    assertTrue(
      "Expected multiple varying maze layouts & algorithms across levels 1..60, got $layoutsSeen",
      layoutsSeen.size >= 6,
    )
  }

  @Test
  fun bonusLevels_andArchetypes_produceVaryingSolvableLayouts() {
    for (archetype in LayoutArchetype.entries) {
      val puzzle =
        generator.generate(
          GenerationRequest(
            mode = GameMode.CLASSIC,
            levelNumber = 12,
            difficulty = DifficultyTier.MEDIUM,
            algorithm = MazeAlgorithm.PRIM,
            seed = 98765L,
            archetype = archetype,
            isBonusLevel = true,
          )
        )
      val report = generator.verifySolvable(puzzle)
      assertTrue(
        "Archetype ${archetype.displayName} failed solvability: ${report.failureReason}",
        report.isSolvable,
      )
    }

    val variantA = generator.generateLevel(GameMode.CLASSIC, 14, seed = 11111L)
    val variantB = generator.generateLevel(GameMode.CLASSIC, 14, seed = 99999L)
    assertNotEquals(
      "Different seeds must yield different procedural maze layouts",
      variantA.arrows,
      variantB.arrows,
    )
  }

  @Test
  fun fallStages_1to20_areSolvableWithGravity() {
    for (stage in 1..20) {
      val puzzle = generator.generateLevel(GameMode.FALL, stage)
      val report = generator.verifySolvable(puzzle)
      assertTrue(
        "Fall stage $stage (${puzzle.layoutName}) failed gravity solvability: ${report.failureReason}",
        report.isSolvable,
      )
      assertEquals(puzzle.arrows.size, report.solutionOrder.size)
    }
  }

  @Test
  fun verifySolvable_rejectsDeadlockedCycle() {
    val cycle =
      listOf(
        ArrowEntity(id = 1, cells = listOf(GridPoint(1, 1), GridPoint(1, 2)), direction = Direction.DOWN),
        ArrowEntity(id = 2, cells = listOf(GridPoint(1, 3), GridPoint(2, 3)), direction = Direction.RIGHT),
        ArrowEntity(id = 3, cells = listOf(GridPoint(3, 3), GridPoint(3, 2)), direction = Direction.UP),
        ArrowEntity(id = 4, cells = listOf(GridPoint(3, 1), GridPoint(2, 1)), direction = Direction.LEFT),
      )
    val level =
      PuzzleLevel(
        mode = GameMode.CLASSIC,
        levelNumber = 99,
        cols = 5,
        rows = 5,
        arrows = cycle,
      )

    val report = generator.verifySolvable(level)
    assertFalse("Pinwheel deadlock must be reported as unsolvable", report.isSolvable)
  }
}
