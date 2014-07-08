package com.xsens.valedohome;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.text.Layout;
import android.widget.LinearLayout;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;

public class QuaternionGraph {
    private final Handler mHandler = new Handler();
    private Runnable mTimer;
    private GraphView graphView;

    private GraphViewSeries graphSeriesW;
    private GraphViewSeries graphSeriesX;
    private GraphViewSeries graphSeriesY;
    private GraphViewSeries graphSeriesZ;

    private double lastX= 1d;

    public QuaternionGraph() {
        graphSeriesW = new GraphViewSeries(new GraphViewData[] {new GraphViewData(0,0)});
        graphSeriesX = new GraphViewSeries(new GraphViewData[] {new GraphViewData(0,0)});
        graphSeriesY = new GraphViewSeries(new GraphViewData[] {new GraphViewData(0,0)});
        graphSeriesZ = new GraphViewSeries(new GraphViewData[] {new GraphViewData(0,0)});
    }

    public void showGraph(Activity activity, LinearLayout layout) {
        graphView = new LineGraphView(activity, "Quaternion data");
        ((LineGraphView) graphView).setDrawBackground(true);
        graphView.addSeries(graphSeriesW);
        graphView.addSeries(graphSeriesX);
        graphView.addSeries(graphSeriesY);
        graphView.addSeries(graphSeriesZ);
        graphView.setViewPort(1,100);
        graphView.setScalable(false);
        graphView.setScrollable(true);
        ((LineGraphView) graphView).setDrawBackground(true);
        graphView.setBackgroundColor(Color.GRAY);

        layout.addView(graphView);
    }

    public void addDataPoint(float w, float x, float y, float z) {
        lastX += 1d;
        graphSeriesW.appendData(new GraphViewData(lastX, w), true, 100);
        graphSeriesX.appendData(new GraphViewData(lastX, x), true, 100);
        graphSeriesY.appendData(new GraphViewData(lastX, y), true, 100);
        graphSeriesZ.appendData(new GraphViewData(lastX, z), true, 100);
    }

}
