package com.xsens.valedohome;

public class RenderLimiter
{
	private long m_previousUpdate;
	private long m_minimumTimeDiffMillis;

	public RenderLimiter(float maxFrequency)
	{
		m_minimumTimeDiffMillis = (long)(1000.0f / maxFrequency);
		m_previousUpdate = 0;
	}

	public boolean canRequestRender()
	{
		long t = System.currentTimeMillis();
		if (t - m_previousUpdate > m_minimumTimeDiffMillis) {
			m_previousUpdate = t;
			return true;
		}
		return false;
	}
}
