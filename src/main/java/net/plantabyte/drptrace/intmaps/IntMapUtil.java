package net.plantabyte.drptrace.intmaps;

import net.plantabyte.drptrace.IntMap;
import net.plantabyte.drptrace.geometry.Vec2i;

import java.util.ArrayDeque;
import java.util.LinkedList;

/**
 * This class provides static utility functions to facilitate the usage of <code>IntMap</code>s
 */
public class IntMapUtil {

	/**
	 * Performs a flood-fill operation in <code>source</code>, setting the corresponding
	 * filled bits in <code>searchedMap</code> to 1.
	 * @param source Source IntMap
	 * @param searchedMap Map used to keep track of what is (already) filled
	 * @param x x coordinate of start of flood fill
	 * @param y y coordinate of start of flood fill
	 */
	public static void floodFill(final IntMap source, final ZOrderBinaryMap searchedMap, final int x, final int y) {
		final int color = source.get(x, y);
		if (searchedMap.get(x, y) != 0) {
			return; // Already filled
		}
		searchedMap.set(x, y, (byte) 1);

		final var xQueue = new ArrayDeque<Integer>();
		final var yQueue = new ArrayDeque<Integer>();
		xQueue.add(x);
		yQueue.add(y);

		while (!xQueue.isEmpty()) {
			final int currentX = xQueue.poll();
			final int currentY = yQueue.poll();

			// Check all four directions
			// Up
			checkAndEnqueue(source, searchedMap, color, currentX, currentY - 1, xQueue, yQueue);
			// Left
			checkAndEnqueue(source, searchedMap, color, currentX - 1, currentY, xQueue, yQueue);
			// Down
			checkAndEnqueue(source, searchedMap, color, currentX, currentY + 1, xQueue, yQueue);
			// Right
			checkAndEnqueue(source, searchedMap, color, currentX + 1, currentY, xQueue, yQueue);
		}
	}

	private static void checkAndEnqueue(
			final IntMap source, final ZOrderBinaryMap searchedMap, final int targetColor,
			final int x, final int y,
			final ArrayDeque<Integer> xQueue, final ArrayDeque<Integer> yQueue
	) {
		if (source.isInRange(x, y)
				&& source.get(x, y) == targetColor
				&& searchedMap.get(x, y) == 0) {
			searchedMap.set(x, y, (byte) 1);
			xQueue.add(x);
			yQueue.add(y);
		}
	}
}
