package com.xsens.valedohome;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This class implements our custom renderer. Note that the GL10 parameter passed in is unused for OpenGL ES 2.0
 * renderers -- the static class GLES20 is used instead.
 */
public class XkfCe3DRenderer implements GLSurfaceView.Renderer
{
	/** Used for debug logs. */
	private static final String TAG = "XkfCe3DRenderer";

	private final Context mActivityContext;

	private float[] m_bgColor = {0,0,0};
	public void setBackgroundColor(float r, float g, float b)
	{
		m_bgColor[0] = r;
		m_bgColor[1] = g;
		m_bgColor[2] = b;
	}

	/**
	 * Store the model matrix. This matrix is used to move models from object space (where each model can be thought
	 * of being located at the center of the universe) to world space.
	 */
	private float[] mModelMatrix = new float[16];
	
	private float[] m_rotationMatrix = new float[16];
	public void setRotationMatrix(float[] rotation)
	{
		m_rotationMatrix = rotation;
	}

	/**
	 * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space;
	 * it positions things relative to our eye.
	 */
	private float[] mViewMatrix = new float[16];

	/** Store the projection matrix. This is used to project the scene onto a 2D viewport. */
	private float[] mProjectionMatrix = new float[16];

	/** Allocate storage for the final combined matrix. This will be passed into the shader program. */
	private float[] mMVPMatrix = new float[16];

	/**
	 * Stores a copy of the model matrix specifically for the light position.
	 */
	private float[] mLightModelMatrix = new float[16];

	/** Store our model data in a float buffer. */
	private FloatBuffer mCubePositions;
	private FloatBuffer mCubeColors;
	private FloatBuffer mCubeNormals;
	private FloatBuffer mCubeTextureCoordinates;

	/** This will be used to pass in the transformation matrix. */
	private int mMVPMatrixHandle;

	/** This will be used to pass in the modelview matrix. */
	private int mMVMatrixHandle;

	/** This will be used to pass in the light position. */
	private int mLightPosHandle;

	/** This will be used to pass in the texture. */
	private int mTextureUniformHandle;

	/** This will be used to pass in model position information. */
	private int mPositionHandle;

	/** This will be used to pass in model /olor information. */
	private int mColorHandle;

	/** This will be used to pass in model normal information. */
	private int mNormalHandle;

	/** This will be used to pass in model texture coordinate information. */
	private int mTextureCoordinateHandle;

	/** How many bytes per float. */
	private final int mBytesPerFloat = 4;

	/** Size of the position data in elements. */
	private final int mPositionDataSize = 3;

	/** Size of the color data in elements. */
	private final int mColorDataSize = 4;

	/** Size of the normal data in elements. */
	private final int mNormalDataSize = 3;

	/** Size of the texture coordinate data in elements. */
	private final int mTextureCoordinateDataSize = 2;

	/** Used to hold a light centered on the origin in model space. We need a 4th coordinate so we can get translations to work when
	 *  we multiply this by our transformation matrices. */
	private final float[] mLightPosInModelSpace = new float[] {0.0f, 0.0f, 0.0f, 1.0f};

	/** Used to hold the current position of the light in world space (after transformation via model matrix). */
	private final float[] mLightPosInWorldSpace = new float[4];

	/** Used to hold the transformed position of the light in eye space (after transformation via modelview matrix) */
	private final float[] mLightPosInEyeSpace = new float[4];

	/** This is a handle to our cube shading program. */
	private int mProgramHandle;

	/** This is a handle to our texture data. */
	private int[] mTextureDataHandle = new int[6];

	public void setViewInsideOut(boolean insideOut)
	{
		float sizer = 1;
		float wallSign = 1;
		if (insideOut == true) {
			sizer = 7;
			wallSign = -1;
		}

		final float boxSizeX = 0.569f * sizer;
		final float boxSizeY = 0.4f * sizer;
		final float boxSizeZ = 0.8f * sizer;

		// Define points for a cube.

		// X, Y, Z
		final float[] cubePositionData =
		{
			// In OpenGL counter-clockwise winding is default. This means that when we look at a triangle,
			// if the points are counter-clockwise we are looking at the "front". If not we are looking at
			// the back. OpenGL has an optimization where all back-facing triangles are culled, since they
			// usually represent the backside of an object and aren't visible anyways.

			// Front face
			-boxSizeX, boxSizeY, wallSign * boxSizeZ,
			-boxSizeX, -boxSizeY, wallSign * boxSizeZ,
			boxSizeX, boxSizeY, wallSign * boxSizeZ,
			-boxSizeX, -boxSizeY, wallSign * boxSizeZ,
			boxSizeX, -boxSizeY, wallSign * boxSizeZ,
			boxSizeX, boxSizeY, wallSign * boxSizeZ,

			// Right face
			wallSign * boxSizeX, boxSizeY, boxSizeZ,
			wallSign * boxSizeX, -boxSizeY, boxSizeZ,
			wallSign * boxSizeX, boxSizeY, -boxSizeZ,
			wallSign * boxSizeX, -boxSizeY, boxSizeZ,
			wallSign * boxSizeX, -boxSizeY, -boxSizeZ,
			wallSign * boxSizeX, boxSizeY, -boxSizeZ,

			// Back face
			boxSizeX, boxSizeY, wallSign * -boxSizeZ,
			boxSizeX, -boxSizeY, wallSign * -boxSizeZ,
			-boxSizeX, boxSizeY, wallSign * -boxSizeZ,
			boxSizeX, -boxSizeY, wallSign * -boxSizeZ,
			-boxSizeX, -boxSizeY, wallSign * -boxSizeZ,
			-boxSizeX, boxSizeY, wallSign * -boxSizeZ,

			// Left face
			wallSign * -boxSizeX, boxSizeY, -boxSizeZ,
			wallSign * -boxSizeX, -boxSizeY, -boxSizeZ,
			wallSign * -boxSizeX, boxSizeY, boxSizeZ,
			wallSign * -boxSizeX, -boxSizeY, -boxSizeZ,
			wallSign * -boxSizeX, -boxSizeY, boxSizeZ,
			wallSign * -boxSizeX, boxSizeY, boxSizeZ,

			// Top face
			-boxSizeX, wallSign * boxSizeY, -boxSizeZ,
			-boxSizeX, wallSign * boxSizeY, boxSizeZ,
			boxSizeX, wallSign * boxSizeY, -boxSizeZ,
			-boxSizeX, wallSign * boxSizeY, boxSizeZ,
			boxSizeX, wallSign * boxSizeY, boxSizeZ,
			boxSizeX, wallSign * boxSizeY, -boxSizeZ,

			// Bottom face
			boxSizeX, wallSign * -boxSizeY, -boxSizeZ,
			boxSizeX, wallSign * -boxSizeY, boxSizeZ,
			-boxSizeX, wallSign * -boxSizeY, -boxSizeZ,
			boxSizeX, wallSign * -boxSizeY, boxSizeZ,
			-boxSizeX, wallSign * -boxSizeY, boxSizeZ,
			-boxSizeX, wallSign * -boxSizeY, -boxSizeZ,
		};

		// R, G, B, A
		final float[] cubeColorData =
		{
			// Front face
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,

			// Right face
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,

			// Back face
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,

			// Left face
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,

			// Top face
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,

			// Bottom face
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
			1.0f, 1.0f, 1.0f, 1.0f,
		};

		// X, Y, Z
		// The normal is used in light calculations and is a vector which points
		// orthogonal to the plane of the surface. For a cube model, the normals
		// should be orthogonal to the points of each face.
		final float[] cubeNormalData =
		{
			// Front face
			0.0f, 0.0f, 1.0f,
			0.0f, 0.0f, 1.0f,
			0.0f, 0.0f, 1.0f,
			0.0f, 0.0f, 1.0f,
			0.0f, 0.0f, 1.0f,
			0.0f, 0.0f, 1.0f,

			// Right face
			1.0f, 0.0f, 0.0f,
			1.0f, 0.0f, 0.0f,
			1.0f, 0.0f, 0.0f,
			1.0f, 0.0f, 0.0f,
			1.0f, 0.0f, 0.0f,
			1.0f, 0.0f, 0.0f,

			// Back face
			0.0f, 0.0f, -1.0f,
			0.0f, 0.0f, -1.0f,
			0.0f, 0.0f, -1.0f,
			0.0f, 0.0f, -1.0f,
			0.0f, 0.0f, -1.0f,
			0.0f, 0.0f, -1.0f,

			// Left face
			-1.0f, 0.0f, 0.0f,
			-1.0f, 0.0f, 0.0f,
			-1.0f, 0.0f, 0.0f,
			-1.0f, 0.0f, 0.0f,
			-1.0f, 0.0f, 0.0f,
			-1.0f, 0.0f, 0.0f,

			// Top face
			0.0f, 1.0f, 0.0f,
			0.0f, 1.0f, 0.0f,
			0.0f, 1.0f, 0.0f,
			0.0f, 1.0f, 0.0f,
			0.0f, 1.0f, 0.0f,
			0.0f, 1.0f, 0.0f,

			// Bottom face
			0.0f, -1.0f, 0.0f,
			0.0f, -1.0f, 0.0f,
			0.0f, -1.0f, 0.0f,
			0.0f, -1.0f, 0.0f,
			0.0f, -1.0f, 0.0f,
			0.0f, -1.0f, 0.0f
		};

		// S, T (or X, Y)
		// Texture coordinate data.
		// Because images have a Y axis pointing downward (values increase as you move down the image) while
		// OpenGL has a Y axis pointing upward, we adjust for that here by flipping the Y axis.
		// What's more is that the texture coordinates are the same for every face.
		final float[] cubeTextureCoordinateData =
		{
			// Front face
			0.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 1.0f,
			1.0f, 0.0f,

			// Right face
			0.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 1.0f,
			1.0f, 0.0f,

			// Back face
			0.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 1.0f,
			1.0f, 0.0f,

			// Left face
			0.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 1.0f,
			1.0f, 0.0f,

			// Top face
			0.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 1.0f,
			1.0f, 0.0f,

			// Bottom face
			0.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 0.0f,
			0.0f, 1.0f,
			1.0f, 1.0f,
			1.0f, 0.0f,
		};

		// Initialize the buffers.
		mCubePositions = ByteBuffer.allocateDirect(cubePositionData.length * mBytesPerFloat)
		.order(ByteOrder.nativeOrder()).asFloatBuffer();
		mCubePositions.put(cubePositionData).position(0);

		mCubeColors = ByteBuffer.allocateDirect(cubeColorData.length * mBytesPerFloat)
		.order(ByteOrder.nativeOrder()).asFloatBuffer();
		mCubeColors.put(cubeColorData).position(0);

		mCubeNormals = ByteBuffer.allocateDirect(cubeNormalData.length * mBytesPerFloat)
		.order(ByteOrder.nativeOrder()).asFloatBuffer();
		mCubeNormals.put(cubeNormalData).position(0);

		mCubeTextureCoordinates = ByteBuffer.allocateDirect(cubeTextureCoordinateData.length * mBytesPerFloat)
		.order(ByteOrder.nativeOrder()).asFloatBuffer();
		mCubeTextureCoordinates.put(cubeTextureCoordinateData).position(0);
	}

	/**
	 * Initialize the model data.
	 */
	public XkfCe3DRenderer(final Context activityContext)
	{
		mActivityContext = activityContext;
		setViewInsideOut(false);
	}

	protected String getVertexShader()
	{
		return RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.per_pixel_vertex_shader);
	}

	protected String getFragmentShader()
	{
		return RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.per_pixel_fragment_shader);
	}

	@Override
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config)
	{
		// Set the background clear color to black.
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

		// Use culling to remove back faces.
		GLES20.glEnable(GLES20.GL_CULL_FACE);

		// Enable depth testing
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);

		// Enable texture mapping
		GLES20.glEnable(GLES20.GL_TEXTURE_2D);

		// Position the eye in front of the origin.
		final float eyeX = 0.0f;
		final float eyeY = 0.0f;
		final float eyeZ = -0.5f;

		// We are looking toward the distance
		final float lookX = 0.0f;
		final float lookY = 0.0f;
		final float lookZ = -5.0f;

		// Set our up vector. This is where our head would be pointing were we holding the camera.
		final float upX = 0.0f;
		final float upY = 1.0f;
		final float upZ = 0.0f;

		// Set the view matrix. This matrix can be said to represent the camera position.
		// NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
		// view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
		Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

		final String vertexShader = getVertexShader();
 		final String fragmentShader = getFragmentShader();

		final int vertexShaderHandle = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
		final int fragmentShaderHandle = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

		mProgramHandle = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle,
				new String[] {"a_Position",  "a_Color", "a_Normal", "a_TexCoordinate"});

		// Load the texture
		final int[] textureIds = {
			R.drawable.mx,
			R.drawable.my,
			R.drawable.px,
			R.drawable.py,
			R.drawable.pz,
			R.drawable.mz
		};
		for (int i = 0; i < 6; ++i)
			mTextureDataHandle[i] = TextureHelper.loadTexture(mActivityContext, textureIds[i]);
		Matrix.setIdentityM(m_rotationMatrix, 0);
	}

	@Override
	public void onSurfaceChanged(GL10 glUnused, int width, int height)
	{
		// Set the OpenGL viewport to the same size as the surface.
		GLES20.glViewport(0, 0, width, height);

		// Create a new perspective projection matrix. The height will stay the same
		// while the width will vary as per aspect ratio.
		final float ratio = (float) width / height;
		final float left = -ratio;
		final float right = ratio;
		final float bottom = -1.0f;
		final float top = 1.0f;
		final float near = 1.0f;
		final float far = 10.0f;

		Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
	}

	@Override
	public void onDrawFrame(GL10 glUnused)
	{
		GLES20.glClearColor(m_bgColor[0], m_bgColor[1], m_bgColor[2], 0.0f);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

		final float angleInDegrees = 300;

		// Set our per-vertex lighting program.
		GLES20.glUseProgram(mProgramHandle);

		// Set program handles for cube drawing.
		mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVPMatrix");
		mMVMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVMatrix");
		mLightPosHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_LightPos");
		mTextureUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_Texture");
		mPositionHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Position");
		mColorHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Color");
		mNormalHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Normal");
		mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_TexCoordinate");


		// Calculate position of the light. Rotate and then push into the distance.
		Matrix.setIdentityM(mLightModelMatrix, 0);
		Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, -2.0f);
		Matrix.rotateM(mLightModelMatrix, 0, angleInDegrees, 0.0f, 1.0f, 0.0f);
		Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, 3.5f);

		Matrix.multiplyMV(mLightPosInWorldSpace, 0, mLightModelMatrix, 0, mLightPosInModelSpace, 0);
		Matrix.multiplyMV(mLightPosInEyeSpace, 0, mViewMatrix, 0, mLightPosInWorldSpace, 0);

		float[] temp = new float[16];
		// Draw a cube
		Matrix.setIdentityM(mModelMatrix, 0);
		Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, -3.0f);
		Matrix.multiplyMM(temp, 0, mModelMatrix, 0, m_rotationMatrix, 0);
		System.arraycopy(temp, 0, mModelMatrix, 0, 16);
		// rotate around x axis to have block X-axis pointing north (UWS --> NWU)
		Matrix.rotateM(mModelMatrix, 0, 90, 1.0f, 0.0f, 0.0f);
		// rotate around y axis to have block Y-axis pointing north (NWU --> ENU)
		Matrix.rotateM(mModelMatrix, 0, -90, 0.0f, 1.0f, 0.0f);
		drawCube();
	}

	/**
	 * Draws a cube.
	 */
	private void drawCube()
	{
		// Pass in the position information
		mCubePositions.position(0);
		GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false,
                0, mCubePositions);

		GLES20.glEnableVertexAttribArray(mPositionHandle);

		// Pass in the color information
		mCubeColors.position(0);
		GLES20.glVertexAttribPointer(mColorHandle, mColorDataSize, GLES20.GL_FLOAT, false,
                0, mCubeColors);

		GLES20.glEnableVertexAttribArray(mColorHandle);

		// Pass in the normal information
		mCubeNormals.position(0);
		GLES20.glVertexAttribPointer(mNormalHandle, mNormalDataSize, GLES20.GL_FLOAT, false,
                0, mCubeNormals);

		GLES20.glEnableVertexAttribArray(mNormalHandle);

		// Pass in the texture coordinate information
		mCubeTextureCoordinates.position(0);
		GLES20.glVertexAttribPointer(mTextureCoordinateHandle, mTextureCoordinateDataSize, GLES20.GL_FLOAT, false,
                0, mCubeTextureCoordinates);

		GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

		// This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
		// (which currently contains model * view).
		Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

		// Pass in the modelview matrix.
		GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVPMatrix, 0);

		// This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
		// (which now contains model * view * projection).
		Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);

		// Pass in the combined matrix.
		GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

		// Pass in the light position in eye space.
		GLES20.glUniform3f(mLightPosHandle, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2]);

		// Draw the cube.
		//
		for (int i = 0; i < 6; ++i) {
			// Set the active texture unit to texture unit 0.
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

			// Bind the texture to this unit.
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDataHandle[i]);

			// Tell the texture uniform sampler to use this
			// texture in the shader by binding to texture unit 0.
			GLES20.glUniform1i(mTextureUniformHandle, 0);
			GLES20.glDrawArrays(GLES20.GL_TRIANGLES, i * 6, 6);
		}
	}
}
