package com.xsens.valedohome;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.text.Layout;
import android.widget.LinearLayout;

import com.jjoe64.graphview.CustomLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;

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
        //Create quaternion data series, with the correct style, and a datapoint to start it off
        // (without the data point, a indexOutOfBounds exc. happens)
        graphSeriesW = new GraphViewSeries("W", new GraphViewSeriesStyle(0xff000000, 2), new GraphViewData[] {new GraphViewData(0,0)});
        graphSeriesX = new GraphViewSeries("X", new GraphViewSeriesStyle(0xffff0000, 2), new GraphViewData[] {new GraphViewData(0,0)});
        graphSeriesY = new GraphViewSeries("Y", new GraphViewSeriesStyle(0xff00ff00, 2), new GraphViewData[] {new GraphViewData(0,0)});
        graphSeriesZ = new GraphViewSeries("Z", new GraphViewSeriesStyle(0xff0000ff, 2), new GraphViewData[] {new GraphViewData(0,0)});
    }

    public void showGraph(Activity activity, LinearLayout layout) {
        graphView = new LineGraphView(activity, "Quaternion data");
        ((LineGraphView) graphView).setDrawBackground(false);

        graphView.addSeries(graphSeriesW);
        graphView.addSeries(graphSeriesX);
        graphView.addSeries(graphSeriesY);
        graphView.addSeries(graphSeriesZ);

        graphView.setViewPort(0,100);
        graphView.setScalable(false);
        graphView.setScrollable(true);
        graphView.setLegendAlign(GraphView.LegendAlign.TOP);
        graphView.setShowLegend(true);

        graphView.setManualYAxisBounds(1, -1);
        graphView.getGraphViewStyle().setNumVerticalLabels(5);
        graphView.getGraphViewStyle().setNumHorizontalLabels(6);

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
