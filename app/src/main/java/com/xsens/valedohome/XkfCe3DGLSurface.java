package com.xsens.valedohome;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;


public class XkfCe3DGLSurface extends GLSurfaceView
{
	private XkfCe3DRenderer mRenderer;

	// Offsets for touch events
	private float mPreviousX;
	private float mPreviousY;

	private float mDensity;

	public XkfCe3DGLSurface(Context context)
	{
		super(context);
		m_limiter = new RenderLimiter(60);
	}

	public XkfCe3DGLSurface(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	@Override
	public void onPause()
	{
		super.onPause();
	}

	@Override
	public void onResume()
	{
		super.onResume();
	}

	public void setRotationMatrix(float[] rot)
	{
		mRenderer.setRotationMatrix(rot);
		if (m_limiter.canRequestRender())
			requestRender();
	}

	public void setBackgroundColor(float r, float g, float b)
	{
		mRenderer.setBackgroundColor(r, g, b);
	}

	public void setViewInsideOut(boolean insideOut)
	{
		mRenderer.setViewInsideOut(insideOut);
	}
	
	// Hides superclass method.
	public void setRenderer(XkfCe3DRenderer renderer, float density)
	{
		mRenderer = renderer;
		mDensity = density;
		super.setRenderer(renderer);
		setRenderMode(RENDERMODE_WHEN_DIRTY);
	}

	private RenderLimiter m_limiter;
}
