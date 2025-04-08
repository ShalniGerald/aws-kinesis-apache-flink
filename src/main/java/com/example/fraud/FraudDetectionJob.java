package com.example.fraud;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.util.Collector;

import com.example.fraud.model.Transaction;
import com.example.fraud.util.SnsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FraudDetectionJob {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        Properties consumerConfig = new Properties();
        consumerConfig.setProperty("aws.region", "us-east-1");
        consumerConfig.setProperty("stream.initial.position", "LATEST");

        FlinkKinesisConsumer<String> kinesisConsumer =
                new FlinkKinesisConsumer<>("transaction-stream", new SimpleStringSchema(), consumerConfig);

        env.getConfig().setAutoWatermarkInterval(1000);

        env.addSource(kinesisConsumer)
                .map(json -> objectMapper.readValue(json, Transaction.class))
                .assignTimestampsAndWatermarks(WatermarkStrategy.<Transaction>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner((txn, ts) -> txn.timestamp))
                .keyBy(txn -> txn.userId)
                .process(new FraudDetector())
                .map(Transaction::toString)
                .print();

        env.execute("Real-Time Fraud Detection Job");
    }

    public static class FraudDetector extends KeyedProcessFunction<String, Transaction, Transaction> {

        private transient ListState<Long> timestampState;

        @Override
        public void open(Configuration parameters) {
            ListStateDescriptor<Long> descriptor = new ListStateDescriptor<>("timestamps", Long.class);
            timestampState = getRuntimeContext().getListState(descriptor);
            
        }

        @Override
        public void processElement(Transaction txn, Context ctx, Collector<Transaction> out) throws Exception {
            boolean isFraud = false;

            // Rule 1: High amount
            if (txn.amount > 10000) {
                isFraud = true;
            }

            // Rule 2: High velocity
            long oneMinuteAgo = txn.timestamp - Time.minutes(1).toMilliseconds();
            List<Long> timestamps = new ArrayList<>();
            System.out.println(timestampState.get());
            for (Long ts : timestampState.get()) {
                if (ts >= oneMinuteAgo) {
                    timestamps.add(ts);
                }
            }
            timestamps.add(txn.timestamp);
            timestampState.update(timestamps);

            if (timestamps.size() > 5) {
                isFraud = true;
            }

            if (isFraud) {
                String message = "FRAUD DETECTED: " + txn.toString();
                SnsPublisher.publishAlert(message);
                out.collect(txn);
            }
        }
    }
}
