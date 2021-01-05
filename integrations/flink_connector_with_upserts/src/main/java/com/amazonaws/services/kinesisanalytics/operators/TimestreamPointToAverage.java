package com.amazonaws.services.kinesisanalytics.operators;

import com.amazonaws.services.timestream.TimestreamPoint;
import com.amazonaws.services.timestreamwrite.model.MeasureValueType;
import com.google.common.reflect.TypeToken;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import java.util.HashMap;
import java.util.Map;
import com.google.common.collect.Iterables;
import java.util.stream.StreamSupport;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.stream.Collectors;

public class TimestreamPointToAverage implements WindowFunction<TimestreamPoint, TimestreamPoint, Integer, TimeWindow> {
  @Override
  public void apply(Integer key, TimeWindow timeWindow, Iterable<TimestreamPoint> iterable, Collector<TimestreamPoint> collector) {

    //calculate the average
    double sumPoint = StreamSupport
        .stream(iterable.spliterator(), false)
        .mapToDouble(point -> Double.parseDouble(point.getMeasureValue()))
        .sum();

    double avgMeasureValue = sumPoint / Iterables.size(iterable);

    //create a new point to store the averaged point
    TimestreamPoint dataPoint = new TimestreamPoint();
    
    //get the maximum timestamp from the time window, set as the time for the averaged point
    long maxTime = timeWindow.getEnd();
    dataPoint.setTime(maxTime);
    dataPoint.setTimeUnit("MILLISECONDS");
    
    //get and set the measure name and value type
    String measureName = Iterables.get(iterable, 0).getMeasureName();
    dataPoint.setMeasureName("avg_" + measureName);
    dataPoint.setMeasureValue(String.valueOf(avgMeasureValue));
    dataPoint.setMeasureValueType("DOUBLE");
    
    //get and set all dimensions for the point
    Map<String, String> dimensions = Iterables.get(iterable, 0).getDimensions();
    dataPoint.setDimensions(dimensions);

    //debugging
    long minTime = timeWindow.getStart();
    DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
    
    String timestamps = StreamSupport
                    .stream(iterable.spliterator(), false)
                    .map(point -> format.format(point.getTime() * 1000L))
                    .collect(Collectors.joining(", ", "{", "}"));
    
    //add a dimension for the number of records within the time window
    dataPoint.addDimension("records_in_window", String.valueOf(Iterables.size(iterable)));
    
    dataPoint.addDimension("window_start", format.format(minTime));
    
    dataPoint.addDimension("window_end", format.format(maxTime));
    
    dataPoint.addDimension("timestamps_in_window", timestamps);
    //end debugging
    
    collector.collect(dataPoint);
  }
}
