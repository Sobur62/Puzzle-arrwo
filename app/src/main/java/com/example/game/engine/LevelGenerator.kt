package com.example.game.engine

import com.example.game.model.ArrowEntity
import com.example.game.model.Direction
import com.example.game.model.GameMode
import com.example.game.model.GridPoint
import com.example.game.model.PuzzleLevel
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

// Maze difficulty tiers (Easy, Medium, Hard) controlling path complexity and arrow length
enum class DifficultyTier(
  val displayName: String,
  val defaultDimensions: MazeDimensions,
  val minArrowLen: Int,
  val maxArrowLen: Int,
  val turnChance: Double,
  val targetFillRatio: Double,
) {
  EASY(
    displayName = "Easy",
    defaultDimensions = MazeDimensions(cols = 7, rows = 8),
    minArrowLen = 2,
    maxArrowLen = 5,
    turnChance = 0.22,
    targetFillRatio = 0.70,
  ),
  MEDIUM(
    displayName = "Medium",
    defaultDimensions = MazeDimensions(cols = 12, rows = 15),
    minArrowLen = 3,
    maxArrowLen = 12,
    turnChance = 0.42,
    targetFillRatio = 0.82,
  ),
  HARD(
    displayName = "Hard",
    defaultDimensions = MazeDimensions(cols = 20, rows = 25),
    minArrowLen = 4,
    maxArrowLen = 22,
    turnChance = 0.58,
    targetFillRatio = 0.89,
  );

  companion object {
    // Automatically maps level number to a DifficultyTier
    fun forLevel(levelNumber: Int, isBonusLevel: Boolean = false): DifficultyTier {
      if (isBonusLevel) return HARD
      return when {
        levelNumber <= 4 -> EASY
        levelNumber <= 20 -> MEDIUM
        else -> HARD
      }
    }
  }
}

// Maze grid dimensions (columns and rows)
data class MazeDimensions(
  val cols: Int,
  val rows: Int,
) {
  init {
    require(cols >= 4) { "Maze cols must be at least 4 (was $cols)" }
    require(rows >= 4) { "Maze rows must be at least 4 (was $rows)" }
  }
}

// Spanning-tree maze carving algorithm (Kruskal's or Prim's)
enum class MazeAlgorithm(val displayName: String) {
  KRUSKAL("Kruskal"),
  PRIM("Prim"),
}

// Procedural maze layout archetypes producing distinct arrow maze patterns per level
enum class LayoutArchetype(val displayName: String) {
  OPEN_GRID("Open Grid"),
  CENTER_CHAMBER("Center Chamber"),
  DIAMOND_MAZE("Diamond Maze"),
  CROSS_CORRIDORS("Cross Corridors"),
  TWIN_CHAMBERS("Twin Chambers"),
  SPIRAL_POCKETS("Spiral Pockets"),
  SCATTERED_VOID("Scattered Voids"),
}

// Configuration request for procedural level generation
data class GenerationRequest(
  val mode: GameMode,
  val levelNumber: Int,
  val difficulty: DifficultyTier? = null,
  val dimensions: MazeDimensions? = null,
  val algorithm: MazeAlgorithm? = null,
  val seed: Long? = null,
  val archetype: LayoutArchetype? = null,
  val isBonusLevel: Boolean = false,
)

// Mathematical solvability verification report
data class SolvabilityReport(
  val isSolvable: Boolean,
  val solutionOrder: List<Int> = emptyList(),
  val failureReason: String? = null,
)

// Procedural level generator service interface
interface LevelGenerator {
  fun generate(request: GenerationRequest): PuzzleLevel

  fun generateLevel(
    mode: GameMode,
    levelNumber: Int,
    seed: Long? = null,
    isBonusLevel: Boolean = false,
  ): PuzzleLevel

  fun resolveDifficultyConfig(
    mode: GameMode,
    levelNumber: Int,
    difficulty: DifficultyTier?,
    dimensionsOverride: MazeDimensions?,
    isBonusLevel: Boolean = false,
  ): DifficultyConfig

  fun verifySolvable(level: PuzzleLevel): SolvabilityReport

  fun findBlockingPoint(
    arrow: ArrowEntity,
    allArrows: List<ArrowEntity>,
    cols: Int,
    rows: Int,
  ): GridPoint?

  fun isArrowFree(
    arrow: ArrowEntity,
    allArrows: List<ArrowEntity>,
    cols: Int,
    rows: Int,
  ): Boolean

  fun applyGravity(arrows: List<ArrowEntity>, rows: Int): List<ArrowEntity>

  fun findHintArrow(
    mode: GameMode,
    arrows: List<ArrowEntity>,
    cols: Int,
    rows: Int,
  ): ArrowEntity?

  fun isSolvableWithGravity(
    cols: Int,
    rows: Int,
    initialArrows: List<ArrowEntity>,
    maxStates: Int = 600,
  ): Boolean
}

// Resolved grid dimensions and path complexity parameters
data class DifficultyConfig(
  val tier: DifficultyTier,
  val dimensions: MazeDimensions,
  val minArrowLen: Int,
  val maxArrowLen: Int,
  val turnChance: Double,
  val targetFillRatio: Double,
)

/**
 * Procedural arrow maze generator powered by Kruskal's and Prim's spanning-tree algorithms.
 *
 * Pipeline:
 * 1. **Difficulty & Dimensions Resolution**: Selects grid size, arrow length bounds,
 *    `turnChance`, and `targetFillRatio` from `DifficultyTier` and `MazeDimensions`.
 * 2. **Maze Mask & Spanning-Tree Carving (Kruskal / Prim)**:
 *    - Carves a connected spanning-tree corridor graph over playable cells using
 *      Randomized **Kruskal's** (Union-Find) or Randomized **Prim's** (frontier expansion).
 * 3. **Reverse-Peeling Solvable Arrow Extraction**:
 *    - Places arrows along spanning-tree edges so each arrow's exit ray is 100% clear
 *      relative to earlier arrows and never self-blocks.
 * 4. **Forward Verification (`verifySolvable`)**:
 *    - Verifies Classic levels with topological forward peeling and Fall levels with gravity DFS.
 */
class ProceduralLevelGenerator : LevelGenerator {

  private data class GridEdge(
    val u: Int,
    val v: Int,
    val weight: Int,
  )

  private class DisjointSet(size: Int) {
    private val parent = IntArray(size) { it }
    private val rank = IntArray(size)

    fun find(x: Int): Int {
      var curr = x
      while (parent[curr] != curr) {
        parent[curr] = parent[parent[curr]]
        curr = parent[curr]
      }
      return curr
    }

    fun union(a: Int, b: Int): Boolean {
      val ra = find(a)
      val rb = find(b)
      if (ra == rb) return false
      when {
        rank[ra] < rank[rb] -> parent[ra] = rb
        rank[ra] > rank[rb] -> parent[rb] = ra
        else -> {
          parent[rb] = ra
          rank[ra]++
        }
      }
      return true
    }
  }

  override fun generateLevel(
    mode: GameMode,
    levelNumber: Int,
    seed: Long?,
    isBonusLevel: Boolean,
  ): PuzzleLevel {
    return generate(
      GenerationRequest(
        mode = mode,
        levelNumber = levelNumber,
        seed = seed,
        isBonusLevel = isBonusLevel,
      )
    )
  }

  override fun generate(request: GenerationRequest): PuzzleLevel {
    val safeLevel = request.levelNumber.coerceAtLeast(1)
    val config =
      resolveDifficultyConfig(
        mode = request.mode,
        levelNumber = safeLevel,
        difficulty = request.difficulty,
        dimensionsOverride = request.dimensions,
        isBonusLevel = request.isBonusLevel,
      )
    return when (request.mode) {
      GameMode.CLASSIC -> generateClassicLevel(safeLevel, config, request)
      GameMode.FALL -> generateFallLevel(safeLevel, config, request)
    }
  }

  override fun resolveDifficultyConfig(
    mode: GameMode,
    levelNumber: Int,
    difficulty: DifficultyTier?,
    dimensionsOverride: MazeDimensions?,
    isBonusLevel: Boolean,
  ): DifficultyConfig {
    val safeLevel = levelNumber.coerceAtLeast(1)
    val tier = difficulty ?: DifficultyTier.forLevel(safeLevel, isBonusLevel)

    // Resolve dimensions: honor explicit overrides or scale by mode, level, and difficulty tier
    val dims =
      dimensionsOverride
        ?: if (difficulty != null) {
          difficulty.defaultDimensions
        } else {
          progressiveDimensionsFor(mode, safeLevel, isBonusLevel)
        }

    val maxBoardSide = maxOf(dims.cols, dims.rows)
    val scaledMaxLen =
      when (tier) {
        DifficultyTier.EASY -> min(tier.maxArrowLen, (maxBoardSide * 0.75).toInt().coerceAtLeast(3))
        DifficultyTier.MEDIUM -> min(maxOf(tier.maxArrowLen, maxBoardSide / 2 + 2), maxBoardSide)
        DifficultyTier.HARD -> maxOf(tier.maxArrowLen, (maxBoardSide * 0.7).toInt()).coerceAtMost(30)
      }.coerceAtLeast(tier.minArrowLen)

    val adjustedFill =
      if (mode == GameMode.FALL) {
        (tier.targetFillRatio - 0.08).coerceIn(0.58, 0.80)
      } else {
        tier.targetFillRatio
      }

    return DifficultyConfig(
      tier = tier,
      dimensions = dims,
      minArrowLen = tier.minArrowLen,
      maxArrowLen = scaledMaxLen,
      turnChance = tier.turnChance,
      targetFillRatio = adjustedFill,
    )
  }

  private fun progressiveDimensionsFor(
    mode: GameMode,
    level: Int,
    isBonus: Boolean,
  ): MazeDimensions {
    if (mode == GameMode.FALL) {
      return when {
        level == 1 -> MazeDimensions(7, 8)
        level <= 3 -> MazeDimensions(8, 10)
        level <= 7 -> MazeDimensions(10, 12)
        level <= 15 -> MazeDimensions(12, 15)
        else -> {
          val extra = ((level - 15) / 4).coerceAtMost(4)
          MazeDimensions(14 + extra, 17 + extra)
        }
      }
    }

    val effective = if (isBonus) maxOf(level, 10) else level
    return when {
      effective == 1 -> MazeDimensions(6, 6)
      effective == 2 -> MazeDimensions(7, 7)
      effective <= 4 -> MazeDimensions(8, 9)
      effective <= 8 -> MazeDimensions(10, 12)
      effective <= 15 -> MazeDimensions(14, 17)
      effective <= 30 -> MazeDimensions(20, 25)
      effective <= 45 -> MazeDimensions(26, 32)
      else -> {
        val extra = ((effective - 45) / 5).coerceAtMost(8)
        MazeDimensions(32 + extra, 40 + extra)
      }
    }
  }

  private fun selectArchetype(
    mode: GameMode,
    level: Int,
    dims: MazeDimensions,
    requested: LayoutArchetype?,
    isBonus: Boolean,
    seed: Long,
  ): LayoutArchetype {
    if (requested != null) return requested
    if (dims.cols < 8 || dims.rows < 8) return LayoutArchetype.OPEN_GRID

    if (mode == GameMode.FALL) {
      val fallPool = listOf(LayoutArchetype.OPEN_GRID, LayoutArchetype.TWIN_CHAMBERS, LayoutArchetype.DIAMOND_MAZE)
      return if (level <= 2) LayoutArchetype.OPEN_GRID else fallPool[abs(level + seed.toInt()) % fallPool.size]
    }

    if (isBonus) {
      val bonusPool = listOf(LayoutArchetype.DIAMOND_MAZE, LayoutArchetype.SPIRAL_POCKETS, LayoutArchetype.CENTER_CHAMBER)
      return bonusPool[abs(level + seed.toInt()) % bonusPool.size]
    }

    val allClassic = LayoutArchetype.entries
    val index = ((level - 1) + abs((seed xor (seed ushr 17)).toInt())) % allClassic.size
    return allClassic[index]
  }

  private fun selectAlgorithm(level: Int, requested: MazeAlgorithm?, seed: Long): MazeAlgorithm {
    if (requested != null) return requested
    // Alternate between Kruskal's and Prim's algorithms across levels and seeds
    return if (((level + abs(seed.toInt())) % 2) == 0) MazeAlgorithm.KRUSKAL else MazeAlgorithm.PRIM
  }

  private fun buildPlayableMask(
    cols: Int,
    rows: Int,
    archetype: LayoutArchetype,
    rng: Random,
  ): Array<BooleanArray> {
    val mask = Array(rows) { BooleanArray(cols) { true } }
    val cx = (cols - 1) / 2.0
    val cy = (rows - 1) / 2.0

    when (archetype) {
      LayoutArchetype.OPEN_GRID -> Unit

      LayoutArchetype.CENTER_CHAMBER -> {
        val holeW = (cols / 4).coerceAtLeast(1)
        val holeH = (rows / 4).coerceAtLeast(1)
        val minX = (cols / 2) - holeW / 2
        val maxX = minX + holeW - 1
        val minY = (rows / 2) - holeH / 2
        val maxY = minY + holeH - 1
        for (y in minY..maxY) {
          for (x in minX..maxX) {
            if (x in 1 until cols - 1 && y in 1 until rows - 1) {
              mask[y][x] = false
            }
          }
        }
      }

      LayoutArchetype.DIAMOND_MAZE -> {
        for (y in 0 until rows) {
          for (x in 0 until cols) {
            val nx = abs(x - cx) / (cols / 2.0).coerceAtLeast(1.0)
            val ny = abs(y - cy) / (rows / 2.0).coerceAtLeast(1.0)
            if (nx + ny > 1.32) {
              mask[y][x] = false
            }
          }
        }
      }

      LayoutArchetype.CROSS_CORRIDORS -> {
        val midX = cols / 2
        val midY = rows / 2
        for (y in 0 until rows) {
          for (x in 0 until cols) {
            val isCornerQuadrant =
              abs(x - midX) >= 2 && abs(y - midY) >= 2 && (x < 2 || x >= cols - 2) && (y < 2 || y >= rows - 2)
            if (isCornerQuadrant) {
              mask[y][x] = false
            }
          }
        }
      }

      LayoutArchetype.TWIN_CHAMBERS -> {
        val splitVertically = cols >= rows
        if (splitVertically) {
          val mid = cols / 2
          for (y in 2 until rows - 2) {
            mask[y][mid] = false
          }
        } else {
          val mid = rows / 2
          for (x in 2 until cols - 2) {
            mask[mid][x] = false
          }
        }
      }

      LayoutArchetype.SPIRAL_POCKETS -> {
        val pocketCount = (min(cols, rows) / 3).coerceIn(2, 6)
        repeat(pocketCount) {
          val px = rng.nextInt(2, (cols - 2).coerceAtLeast(3))
          val py = rng.nextInt(2, (rows - 2).coerceAtLeast(3))
          mask[py][px] = false
          mask[rows - 1 - py][cols - 1 - px] = false
        }
      }

      LayoutArchetype.SCATTERED_VOID -> {
        val voidTarget = ((cols * rows) * 0.12).toInt().coerceIn(2, 36)
        repeat(voidTarget) {
          val vx = rng.nextInt(1, (cols - 1).coerceAtLeast(2))
          val vy = rng.nextInt(1, (rows - 1).coerceAtLeast(2))
          mask[vy][vx] = false
        }
      }
    }

    var playable = 0
    for (y in 0 until rows) {
      for (x in 0 until cols) {
        if (mask[y][x]) playable++
      }
    }
    if (playable < 12) {
      for (y in 0 until rows) {
        for (x in 0 until cols) {
          mask[y][x] = true
        }
      }
    }
    return mask
  }

  /**
   * Runs Randomized Kruskal's or Prim's algorithm over playable grid cells
   * to build a connected spanning-tree maze graph.
   */
  private fun carveSpanningTree(
    cols: Int,
    rows: Int,
    playableMask: Array<BooleanArray>,
    algorithm: MazeAlgorithm,
    tier: DifficultyTier,
    rng: Random,
  ): Array<MutableList<Int>> {
    val total = cols * rows
    val adj = Array<MutableList<Int>>(total) { ArrayList(4) }

    fun indexOf(x: Int, y: Int): Int = y * cols + x

    // Bias edge weights by difficulty tier (Easy: straighter corridors; Hard: varied turns)
    fun edgeWeight(isHorizontal: Boolean, x: Int, y: Int): Int {
      val base = rng.nextInt(1000)
      return when (tier) {
        DifficultyTier.EASY -> if ((x + y) % 2 == 0 && isHorizontal) base / 2 else base
        DifficultyTier.MEDIUM -> base
        DifficultyTier.HARD -> base + (if ((x xor y) and 1 == 0) 150 else 0)
      }
    }

    when (algorithm) {
      MazeAlgorithm.KRUSKAL -> {
        val edges = ArrayList<GridEdge>(total * 2)
        for (y in 0 until rows) {
          for (x in 0 until cols) {
            if (!playableMask[y][x]) continue
            val u = indexOf(x, y)
            if (x + 1 < cols && playableMask[y][x + 1]) {
              edges.add(GridEdge(u, indexOf(x + 1, y), edgeWeight(true, x, y)))
            }
            if (y + 1 < rows && playableMask[y + 1][x]) {
              edges.add(GridEdge(u, indexOf(x, y + 1), edgeWeight(false, x, y)))
            }
          }
        }
        edges.sortBy { it.weight }
        val dsu = DisjointSet(total)
        for (e in edges) {
          if (dsu.union(e.u, e.v)) {
            adj[e.u].add(e.v)
            adj[e.v].add(e.u)
          }
        }
      }

      MazeAlgorithm.PRIM -> {
        val inTree = BooleanArray(total)
        val playableStarts = ArrayList<Int>(total)
        for (y in 0 until rows) {
          for (x in 0 until cols) {
            if (playableMask[y][x]) playableStarts.add(indexOf(x, y))
          }
        }
        playableStarts.shuffle(rng)

        val frontier = ArrayList<GridEdge>()
        fun addFrontier(u: Int) {
          val ux = u % cols
          val uy = u / cols
          for (d in Direction.entries) {
            val vx = ux + d.dx
            val vy = uy + d.dy
            if (vx in 0 until cols && vy in 0 until rows && playableMask[vy][vx]) {
              val v = indexOf(vx, vy)
              if (!inTree[v]) {
                frontier.add(GridEdge(u, v, edgeWeight(d.dx != 0, vx, vy)))
              }
            }
          }
        }

        // Carve Prim's spanning tree across every disconnected component if pockets exist
        for (start in playableStarts) {
          if (inTree[start]) continue
          inTree[start] = true
          addFrontier(start)

          while (frontier.isNotEmpty()) {
            // Fast weighted frontier selection
            var bestIdx = 0
            for (i in 1 until frontier.size) {
              if (frontier[i].weight < frontier[bestIdx].weight) {
                bestIdx = i
              }
            }
            val edge = frontier.removeAt(bestIdx)
            if (inTree[edge.v]) continue
            inTree[edge.v] = true
            adj[edge.u].add(edge.v)
            adj[edge.v].add(edge.u)
            addFrontier(edge.v)
          }
        }
      }
    }

    return adj
  }

  private fun generateClassicLevel(
    level: Int,
    config: DifficultyConfig,
    request: GenerationRequest,
  ): PuzzleLevel {
    val dims = config.dimensions
    val baseSeed =
      request.seed
        ?: (level * 7919L + config.tier.ordinal * 3137L + (if (request.isBonusLevel) 44497L else 104729L))

    for (attempt in 0 until MAX_CLASSIC_ATTEMPTS) {
      val attemptSeed = baseSeed + attempt * 1009L
      val rng = Random(attemptSeed)
      val archetype =
        if (attempt < MAX_CLASSIC_ATTEMPTS - 1) {
          selectArchetype(GameMode.CLASSIC, level, dims, request.archetype, request.isBonusLevel, attemptSeed)
        } else {
          LayoutArchetype.OPEN_GRID
        }
      val algorithm = selectAlgorithm(level + attempt, request.algorithm, attemptSeed)
      val mask = buildPlayableMask(dims.cols, dims.rows, archetype, rng)
      val treeAdj = carveSpanningTree(dims.cols, dims.rows, mask, algorithm, config.tier, rng)
      val arrows = packArrowsFromSpanningTree(config, mask, treeAdj, rng)
      val candidate =
        PuzzleLevel(
          mode = GameMode.CLASSIC,
          levelNumber = level,
          cols = dims.cols,
          rows = dims.rows,
          arrows = arrows,
          layoutName = "${archetype.displayName} (${algorithm.displayName})",
        )
      if (arrows.size >= 3 && verifySolvable(candidate).isSolvable) {
        return candidate
      }
    }

    // Guaranteed fallback: plain open grid with Kruskal spanning tree
    val fallbackRng = Random(baseSeed)
    val openMask = Array(dims.rows) { BooleanArray(dims.cols) { true } }
    val treeAdj = carveSpanningTree(dims.cols, dims.rows, openMask, MazeAlgorithm.KRUSKAL, config.tier, fallbackRng)
    val fallbackArrows = packArrowsFromSpanningTree(config, openMask, treeAdj, fallbackRng)
    return PuzzleLevel(
      mode = GameMode.CLASSIC,
      levelNumber = level,
      cols = dims.cols,
      rows = dims.rows,
      arrows = fallbackArrows,
      layoutName = "${LayoutArchetype.OPEN_GRID.displayName} (${MazeAlgorithm.KRUSKAL.displayName})",
    )
  }

  private fun generateFallLevel(
    stage: Int,
    config: DifficultyConfig,
    request: GenerationRequest,
  ): PuzzleLevel {
    val dims = config.dimensions
    val baseSeed = request.seed ?: (stage * 13007L + config.tier.ordinal * 2029L + 5003L)

    for (attempt in 0 until MAX_FALL_ATTEMPTS) {
      val attemptSeed = baseSeed + attempt * 997L
      val rng = Random(attemptSeed)
      val archetype =
        if (attempt < 16) {
          selectArchetype(GameMode.FALL, stage, dims, request.archetype, false, attemptSeed)
        } else {
          LayoutArchetype.OPEN_GRID
        }
      val algorithm = selectAlgorithm(stage + attempt, request.algorithm, attemptSeed)
      val mask = buildPlayableMask(dims.cols, dims.rows, archetype, rng)
      val treeAdj = carveSpanningTree(dims.cols, dims.rows, mask, algorithm, config.tier, rng)
      val arrows = packArrowsFromSpanningTree(config, mask, treeAdj, rng)
      val candidate =
        PuzzleLevel(
          mode = GameMode.FALL,
          levelNumber = stage,
          cols = dims.cols,
          rows = dims.rows,
          arrows = arrows,
          layoutName = "${archetype.displayName} (${algorithm.displayName})",
        )
      if (arrows.isNotEmpty() && verifySolvable(candidate).isSolvable) {
        return candidate
      }
    }

    val fallbackConfig =
      DifficultyConfig(
        tier = DifficultyTier.EASY,
        dimensions = MazeDimensions(7, 8),
        minArrowLen = 2,
        maxArrowLen = 4,
        turnChance = 0.0,
        targetFillRatio = 0.60,
      )
    val fallbackMask = Array(8) { BooleanArray(7) { true } }
    val fallbackTree =
      carveSpanningTree(7, 8, fallbackMask, MazeAlgorithm.PRIM, DifficultyTier.EASY, Random(stage.toLong()))
    val fallbackArrows = packArrowsFromSpanningTree(fallbackConfig, fallbackMask, fallbackTree, Random(stage.toLong()))
    return PuzzleLevel(
      mode = GameMode.FALL,
      levelNumber = stage,
      cols = fallbackConfig.dimensions.cols,
      rows = fallbackConfig.dimensions.rows,
      arrows = fallbackArrows,
      layoutName = "${LayoutArchetype.OPEN_GRID.displayName} (${MazeAlgorithm.PRIM.displayName})",
    )
  }

  // Reverse-peeling arrow packing guided by Kruskal/Prim spanning-tree corridors
  private fun packArrowsFromSpanningTree(
    config: DifficultyConfig,
    playableMask: Array<BooleanArray>,
    treeAdj: Array<MutableList<Int>>,
    rng: Random,
  ): List<ArrowEntity> {
    val cols = config.dimensions.cols
    val rows = config.dimensions.rows
    val occupied = Array(rows) { BooleanArray(cols) }

    var playableCount = 0
    val allPoints = ArrayList<GridPoint>(cols * rows)
    for (y in 0 until rows) {
      for (x in 0 until cols) {
        if (playableMask[y][x]) {
          playableCount++
          allPoints.add(GridPoint(x, y))
        }
      }
    }

    var occupiedCount = 0
    val targetOccupied = (playableCount * config.targetFillRatio).toInt().coerceAtLeast(6)
    val placed = ArrayList<ArrowEntity>()
    var nextId = 1

    fun isRayClear(head: GridPoint, dir: Direction): Boolean {
      var cx = head.x + dir.dx
      var cy = head.y + dir.dy
      while (cx in 0 until cols && cy in 0 until rows) {
        if (occupied[cy][cx]) return false
        cx += dir.dx
        cy += dir.dy
      }
      return true
    }

    fun isTreeConnected(a: GridPoint, b: GridPoint): Boolean {
      val idxA = a.y * cols + a.x
      val idxB = b.y * cols + b.x
      return idxB in treeAdj[idxA]
    }

    var stagnantPasses = 0
    while (occupiedCount < targetOccupied && stagnantPasses < 4) {
      var placedInPass = 0
      allPoints.shuffle(rng)

      for (head in allPoints) {
        if (occupiedCount >= targetOccupied) break
        if (!playableMask[head.y][head.x] || occupied[head.y][head.x]) continue

        val validDirs = Direction.entries.filter { dir -> isRayClear(head, dir) }
        if (validDirs.isEmpty()) continue

        // Prefer directions whose neck cell connects along the spanning tree
        val treeLinkedDirs =
          validDirs.filter { dir ->
            val nx = head.x - dir.dx
            val ny = head.y - dir.dy
            nx in 0 until cols &&
              ny in 0 until rows &&
              playableMask[ny][nx] &&
              !occupied[ny][nx] &&
              isTreeConnected(head, GridPoint(nx, ny))
          }
        val dir =
          if (treeLinkedDirs.isNotEmpty() && rng.nextDouble() < 0.85) {
            treeLinkedDirs[rng.nextInt(treeLinkedDirs.size)]
          } else {
            validDirs[rng.nextInt(validDirs.size)]
          }

        val neck = GridPoint(head.x - dir.dx, head.y - dir.dy)
        if (neck.x !in 0 until cols || neck.y !in 0 until rows) continue
        if (!playableMask[neck.y][neck.x] || occupied[neck.y][neck.x]) continue

        val desiredLen = rng.nextInt(config.minArrowLen, config.maxArrowLen + 1)
        val pathReversed = ArrayList<GridPoint>(desiredLen)
        pathReversed.add(head)
        pathReversed.add(neck)

        val selfVisited = HashSet<GridPoint>(desiredLen * 2)
        selfVisited.add(head)
        selfVisited.add(neck)

        // Mark exit ray cells blocked so the arrow body never obstructs its own head
        val rayCells = HashSet<GridPoint>()
        var rx = head.x + dir.dx
        var ry = head.y + dir.dy
        while (rx in 0 until cols && ry in 0 until rows) {
          rayCells.add(GridPoint(rx, ry))
          rx += dir.dx
          ry += dir.dy
        }

        var prevStepDir = Direction.entries.first { it.dx == -dir.dx && it.dy == -dir.dy }
        var curr = neck

        while (pathReversed.size < desiredLen) {
          val candidates =
            Direction.entries.mapNotNull { stepDir ->
              val next = GridPoint(curr.x + stepDir.dx, curr.y + stepDir.dy)
              if (
                next.x in 0 until cols &&
                  next.y in 0 until rows &&
                  playableMask[next.y][next.x] &&
                  !occupied[next.y][next.x] &&
                  next !in selfVisited &&
                  next !in rayCells
              ) {
                stepDir to next
              } else {
                null
              }
            }

          if (candidates.isEmpty()) break

          // Prioritize path extensions that follow spanning-tree corridor edges
          val treeCandidates = candidates.filter { (_, pt) -> isTreeConnected(curr, pt) }
          val activePool =
            if (treeCandidates.isNotEmpty() && rng.nextDouble() < 0.85) treeCandidates else candidates

          val chosen =
            if (activePool.size == 1) {
              activePool[0]
            } else {
              val straight = activePool.firstOrNull { it.first == prevStepDir }
              if (straight != null && rng.nextDouble() >= config.turnChance) {
                straight
              } else {
                val turningPool = activePool.filter { it.first != prevStepDir }.ifEmpty { activePool }
                turningPool.minByOrNull { (_, pt) ->
                  var freeNeighbors = 0
                  for (d in Direction.entries) {
                    val nx = pt.x + d.dx
                    val ny = pt.y + d.dy
                    if (
                      nx in 0 until cols &&
                        ny in 0 until rows &&
                        playableMask[ny][nx] &&
                        !occupied[ny][nx]
                    ) {
                      freeNeighbors++
                    }
                  }
                  freeNeighbors * 10 + rng.nextInt(9)
                }!!
              }
            }

          prevStepDir = chosen.first
          curr = chosen.second
          pathReversed.add(curr)
          selfVisited.add(curr)
        }

        if (pathReversed.size >= 2) {
          val cells = pathReversed.reversed()
          for (pt in cells) {
            occupied[pt.y][pt.x] = true
            occupiedCount++
          }
          placed.add(
            ArrowEntity(
              id = nextId++,
              cells = cells,
              direction = dir,
              colorVariant = (nextId - 1) % 3,
            )
          )
          placedInPass++
        }
      }

      if (placedInPass == 0) {
        stagnantPasses++
      } else {
        stagnantPasses = 0
      }
    }

    return placed
  }

  // Verifies mathematical solvability and structural integrity of the generated level
  override fun verifySolvable(level: PuzzleLevel): SolvabilityReport {
    val arrows = level.arrows
    if (arrows.isEmpty()) {
      return SolvabilityReport(isSolvable = false, failureReason = "Level contains no arrows")
    }

    val seenIds = HashSet<Int>(arrows.size)
    val occupied = HashSet<GridPoint>()
    for (arrow in arrows) {
      if (!seenIds.add(arrow.id)) {
        return SolvabilityReport(isSolvable = false, failureReason = "Duplicate arrow id ${arrow.id}")
      }
      if (arrow.cells.size < 2) {
        return SolvabilityReport(isSolvable = false, failureReason = "Arrow ${arrow.id} shorter than 2 cells")
      }
      for (i in arrow.cells.indices) {
        val c = arrow.cells[i]
        if (c.x !in 0 until level.cols || c.y !in 0 until level.rows) {
          return SolvabilityReport(isSolvable = false, failureReason = "Arrow ${arrow.id} out of bounds at $c")
        }
        if (!occupied.add(c)) {
          return SolvabilityReport(isSolvable = false, failureReason = "Overlapping cell at $c")
        }
        if (i > 0) {
          val prev = arrow.cells[i - 1]
          if (abs(c.x - prev.x) + abs(c.y - prev.y) != 1) {
            return SolvabilityReport(isSolvable = false, failureReason = "Non-adjacent segment in arrow ${arrow.id}")
          }
        }
      }
      val neck = arrow.cells[arrow.cells.size - 2]
      val head = arrow.head
      if (head.x - neck.x != arrow.direction.dx || head.y - neck.y != arrow.direction.dy) {
        return SolvabilityReport(
          isSolvable = false,
          failureReason = "Arrow ${arrow.id} direction ${arrow.direction} mismatches head orientation",
        )
      }
    }

    return when (level.mode) {
      GameMode.CLASSIC -> {
        val remaining = arrows.toMutableList()
        val order = ArrayList<Int>(arrows.size)
        while (remaining.isNotEmpty()) {
          val free = remaining.firstOrNull { isArrowFree(it, remaining, level.cols, level.rows) }
          if (free == null) {
            return SolvabilityReport(
              isSolvable = false,
              solutionOrder = order,
              failureReason = "Deadlocked with ${remaining.size} arrows left",
            )
          }
          order.add(free.id)
          remaining.remove(free)
        }
        SolvabilityReport(isSolvable = true, solutionOrder = order)
      }

      GameMode.FALL -> {
        val order = findGravitySolutionOrder(level.cols, level.rows, arrows, maxStates = 1500)
        if (order != null) {
          SolvabilityReport(isSolvable = true, solutionOrder = order)
        } else {
          SolvabilityReport(isSolvable = false, failureReason = "Fall stage unsolvable under gravity")
        }
      }
    }
  }

  override fun findBlockingPoint(
    arrow: ArrowEntity,
    allArrows: List<ArrowEntity>,
    cols: Int,
    rows: Int,
  ): GridPoint? {
    val occupiedByOthers = HashSet<GridPoint>()
    for (other in allArrows) {
      if (other.id != arrow.id) {
        occupiedByOthers.addAll(other.cells)
      }
    }
    var cx = arrow.head.x + arrow.direction.dx
    var cy = arrow.head.y + arrow.direction.dy
    while (cx in 0 until cols && cy in 0 until rows) {
      val pt = GridPoint(cx, cy)
      if (pt in occupiedByOthers) {
        return pt
      }
      cx += arrow.direction.dx
      cy += arrow.direction.dy
    }
    return null
  }

  override fun isArrowFree(
    arrow: ArrowEntity,
    allArrows: List<ArrowEntity>,
    cols: Int,
    rows: Int,
  ): Boolean = findBlockingPoint(arrow, allArrows, cols, rows) == null

  override fun applyGravity(arrows: List<ArrowEntity>, rows: Int): List<ArrowEntity> {
    if (arrows.isEmpty()) return arrows
    val current = arrows.toMutableList()

    var moved: Boolean
    do {
      moved = false
      val order = current.indices.sortedByDescending { idx -> current[idx].cells.maxOf { it.y } }
      for (idx in order) {
        val arrow = current[idx]
        val otherOccupied = HashSet<GridPoint>()
        for (j in current.indices) {
          if (j != idx) otherOccupied.addAll(current[j].cells)
        }

        var dropRows = 0
        while (true) {
          val nextDrop = dropRows + 1
          val canDrop =
            arrow.cells.all { cell ->
              val ny = cell.y + nextDrop
              ny < rows && GridPoint(cell.x, ny) !in otherOccupied
            }
          if (canDrop) {
            dropRows = nextDrop
          } else {
            break
          }
        }

        if (dropRows > 0) {
          current[idx] =
            arrow.copy(
              cells = arrow.cells.map { GridPoint(it.x, it.y + dropRows) }
            )
          moved = true
        }
      }
    } while (moved)

    return current
  }

  override fun findHintArrow(
    mode: GameMode,
    arrows: List<ArrowEntity>,
    cols: Int,
    rows: Int,
  ): ArrowEntity? {
    val freeArrows = arrows.filter { isArrowFree(it, arrows, cols, rows) }
    if (freeArrows.isEmpty()) return null

    if (mode == GameMode.CLASSIC) {
      return freeArrows.maxByOrNull { candidate ->
        val after = arrows.filter { it.id != candidate.id }
        after.count { isArrowFree(it, after, cols, rows) }
      }
    }

    for (candidate in freeArrows) {
      val after = applyGravity(arrows.filter { it.id != candidate.id }, rows)
      if (isSolvableWithGravity(cols, rows, after)) {
        return candidate
      }
    }
    return freeArrows.firstOrNull()
  }

  override fun isSolvableWithGravity(
    cols: Int,
    rows: Int,
    initialArrows: List<ArrowEntity>,
    maxStates: Int,
  ): Boolean = findGravitySolutionOrder(cols, rows, initialArrows, maxStates) != null

  private fun findGravitySolutionOrder(
    cols: Int,
    rows: Int,
    initialArrows: List<ArrowEntity>,
    maxStates: Int,
  ): List<Int>? {
    if (initialArrows.isEmpty()) return emptyList()
    val visited = HashSet<Long>()
    var statesExplored = 0

    fun stateHash(state: List<ArrowEntity>): Long {
      var h = 1125899906842597L
      for (a in state) {
        h = h * 31L + a.id.toLong()
        h = h * 31L + a.head.x.toLong() * 101L + a.head.y.toLong()
      }
      return h
    }

    fun dfs(state: List<ArrowEntity>, path: MutableList<Int>): List<Int>? {
      if (state.isEmpty()) return path.toList()
      if (statesExplored >= maxStates) return null
      statesExplored++

      val key = stateHash(state)
      if (!visited.add(key)) return null

      val free = state.filter { isArrowFree(it, state, cols, rows) }
      if (free.isEmpty()) return null

      val ordered =
        free.sortedBy { arrow ->
          val edgeDist =
            when (arrow.direction) {
              Direction.UP -> arrow.head.y
              Direction.DOWN -> rows - 1 - arrow.head.y
              Direction.LEFT -> arrow.head.x
              Direction.RIGHT -> cols - 1 - arrow.head.x
            }
          min(edgeDist, abs(arrow.head.y))
        }

      for (chosen in ordered) {
        val next = applyGravity(state.filter { it.id != chosen.id }, rows)
        path.add(chosen.id)
        val solved = dfs(next, path)
        if (solved != null) return solved
        path.removeAt(path.lastIndex)
      }
      return null
    }

    return dfs(initialArrows, ArrayList(initialArrows.size))
  }

  private companion object {
    const val MAX_CLASSIC_ATTEMPTS = 8
    const val MAX_FALL_ATTEMPTS = 24
  }
}
