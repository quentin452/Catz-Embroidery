package fr.iamacat.embroider;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;

public class PEmbroiderBooleanShapeGraphics {
	public static final int UNION        = 1;
	public static final int OR           = 1;
	public static final int XOR          = 2;
	public static final int SYMMETRIC_DIFFERENCE = 2;
	public static final int INTERSECTION = 3;
	public static final int AND          = 3;
	public static final int SUBTRACT     = 4;
	public static final int DIFFERENCE   = 4;

	private int width, height;
	private FrameBuffer frameBuffer;
	private SpriteBatch batch;
	private Texture texture;

	public PEmbroiderBooleanShapeGraphics(int width, int height) {
		this.width = width;
		this.height = height;
		this.batch = new SpriteBatch();

		// Create a FrameBuffer (like PGraphics)
		this.frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
		this.frameBuffer.begin();
		clear();
		this.frameBuffer.end();
	}

	public void operator(int mode) {
		frameBuffer.begin();
		batch.begin();

		switch (mode) {
			case OR:
				batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA); // Normal blend
				break;
			case SUBTRACT:
				batch.setBlendFunction(GL20.GL_ZERO, GL20.GL_ONE_MINUS_SRC_COLOR); // Subtract mode
				break;
			case XOR:
				batch.setBlendFunction(GL20.GL_ONE_MINUS_DST_COLOR, GL20.GL_ONE_MINUS_SRC_COLOR); // XOR-like effect
				break;
			case AND:
				batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO); // Multiply (AND)
				break;
		}

		if (texture != null) {
			batch.draw(texture, 0, 0, width, height);
		}

		batch.end();
		frameBuffer.end();

		// Update texture for future operations
		updateTexture();
	}

	public void union() { operator(UNION); }
	public void or() { operator(OR); }
	public void xor() { operator(XOR); }
	public void and() { operator(AND); }
	public void subtract() { operator(SUBTRACT); }
	public void difference() { operator(DIFFERENCE); }
	public void intersection() { operator(INTERSECTION); }
	public void symmetricDifference() { operator(SYMMETRIC_DIFFERENCE); }

	public void beginOps() {
		frameBuffer.begin();
	}

	public void endOps() {
		frameBuffer.end();
	}

	private void updateTexture() {
		if (texture != null) texture.dispose();
		texture = frameBuffer.getColorBufferTexture();
	}

	private void clear() {
		batch.begin();
		batch.setColor(Color.BLACK);
		batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ZERO); // Set blend mode to overwrite
		batch.end();
	}

	public Texture getTexture() {
		return texture;
	}

	public void dispose() {
		batch.dispose();
		frameBuffer.dispose();
		if (texture != null) texture.dispose();
	}
}
