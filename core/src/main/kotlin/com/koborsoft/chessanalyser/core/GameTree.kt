package com.koborsoft.chessanalyser.core

/**
 * A lépésgráf egy csomópontja: a [move] lépés UTÁNI [position] állással.
 * A gyökérnél a lépés és a SAN null.
 */
class GameNode internal constructor(
    val id: Int,
    val move: Move?,
    val san: String?,
    val position: Position,
    val parent: GameNode?,
) {
    internal val childList = mutableListOf<GameNode>()
    val children: List<GameNode> get() = childList
}

/**
 * Elágazásokat is tároló játszma-fa. A [current] a megjelenített állás;
 * visszalépés után új lépés új oldalágat nyit, a korábbi ág megmarad.
 */
class GameTree(val rootPosition: Position = Position.initial()) {

    private var nextId = 0
    val root = GameNode(nextId++, null, null, rootPosition, null)

    var current: GameNode = root
        private set

    /**
     * Lépés a jelenlegi állásból. Ha a lépés már létezik gyerekágként, oda
     * lép át; különben új ágat nyit. Szabálytalan lépésre false.
     */
    fun play(move: Move): Boolean {
        val existing = current.children.firstOrNull {
            it.move != null && it.move.from == move.from &&
                it.move.to == move.to && it.move.promotion == move.promotion
        }
        if (existing != null) {
            current = existing
            return true
        }
        val legal = MoveGenerator.legalMoves(current.position).firstOrNull {
            it.from == move.from && it.to == move.to && it.promotion == move.promotion
        } ?: return false
        val node = GameNode(
            nextId++,
            legal,
            San.toSan(current.position, legal),
            current.position.applyMove(legal),
            current,
        )
        current.childList.add(node)
        current = node
        return true
    }

    /** Egy lépés vissza a szülő felé; a gyökérnél false. */
    fun undo(): Boolean {
        current = current.parent ?: return false
        return true
    }

    fun goto(node: GameNode) {
        current = node
    }

    fun nodeById(id: Int): GameNode? = findNode(root, id)

    private fun findNode(node: GameNode, id: Int): GameNode? {
        if (node.id == id) return node
        for (child in node.children) findNode(child, id)?.let { return it }
        return null
    }

    /** Út a gyökértől az adott csomópontig (a gyökérrel együtt). */
    fun pathTo(node: GameNode): List<GameNode> {
        val path = ArrayList<GameNode>()
        var cursor: GameNode? = node
        while (cursor != null) {
            path.add(cursor)
            cursor = cursor.parent
        }
        return path.reversed()
    }
}
