package org.iot_simulator;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.*;
import org.apache.commons.cli.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.couchbase.client.java.kv.MutateInSpec.arrayAppend;


public class Main {

    public static void main(String[] args) {

        DocGenerator docGenerator = new SensorDocGenerator();

        /* parameters to use if no command line is found */
        String username = "Administrator";
        String password = "password";
        String ip = "127.0.0.1";
        String bucketName = "sample";
        String scopeName = "_default";
        String collectionName = "_default";
        int sensors = 5;
        int insertsPerSecond = 5;
        int maxTime = 0;
        int millis_span = 60000;


        CommandLine commandLine;
        Option option_h = Option.builder("h").argName("host").hasArg().desc("couchbase ip").build();
        Option option_u = Option.builder("u").argName("username").hasArg().desc("couchbase username").build();
        Option option_p = Option.builder("p").argName("password").hasArg().desc("couchbase password").build();
        Option option_b = Option.builder("b").argName("bucket").hasArg().desc("couchbase bucket").build();
        Option option_f = Option.builder("se").argName("sensors").hasArg().desc("number of sensors to simulate").build();
        Option option_s = Option.builder("s").argName("scope").hasArg().desc("couchbase scope").build();
        Option option_c = Option.builder("c").argName("collection").hasArg().desc("couchbase collection").build();
        Option option_mt = Option.builder("mt").argName("max-seconds").hasArg().desc("max seconds to run").build();
        Option option_ips = Option.builder("ips").argName("inserts-per-second").hasArg().desc("inserts per second for each sensor").build();
        Option option_ms = Option.builder("ms").argName("millis-span").hasArg().desc("milliseconds covered by a timeseries document").build();



        Options options = new Options();
        CommandLineParser parser = new DefaultParser();

        options.addOption(option_h);
        options.addOption(option_u);
        options.addOption(option_p);
        options.addOption(option_b);
        options.addOption(option_c);
        options.addOption(option_s);
        options.addOption(option_f);
        options.addOption(option_mt);
        options.addOption(option_ips);
        options.addOption(option_ms);

        String header = "               [<arg1> [<arg2> [<arg3> ...\n       Options, flags and arguments may be in any order";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("CLIsample", header, options, null, true);


        try {
            commandLine = parser.parse(options, args);

            if (commandLine.hasOption("h")) {
                System.out.printf("host ip: %s%n", commandLine.getOptionValue("h"));
                ip = commandLine.getOptionValue("h");
            }

            if (commandLine.hasOption("u")) {
                System.out.printf("couchbase username: %s%n", commandLine.getOptionValue("u"));
                username = commandLine.getOptionValue("u");
            }

            if (commandLine.hasOption("p")) {
                System.out.printf("couchbase password: %s%n", commandLine.getOptionValue("p"));
                password = commandLine.getOptionValue("p");
            }
            if (commandLine.hasOption("b")) {
                System.out.printf("couchbase bucket: %s%n", commandLine.getOptionValue("b"));
                bucketName = commandLine.getOptionValue("b");
            }
            if (commandLine.hasOption("s")) {
                System.out.printf("couchbase scope: %s%n", commandLine.getOptionValue("s"));
                scopeName = commandLine.getOptionValue("s");
            }
            if (commandLine.hasOption("c")) {
                System.out.printf("couchbase collection: %s%n", commandLine.getOptionValue("c"));
                collectionName = commandLine.getOptionValue("c");
            }
            if (commandLine.hasOption("c")) {
                System.out.printf("couchbase collection: %s%n", commandLine.getOptionValue("c"));
                collectionName = commandLine.getOptionValue("c");
            }
            if (commandLine.hasOption("mt")) {
                System.out.printf("max time to run: %s%n", commandLine.getOptionValue("mt"));
                maxTime = Integer.parseInt(commandLine.getOptionValue("mt"));
            }
            if (commandLine.hasOption("ips")) {
                System.out.printf("inserts per second: %s%n", commandLine.getOptionValue("ips"));
                insertsPerSecond = Integer.parseInt(commandLine.getOptionValue("ips"));
            }
            if (commandLine.hasOption("ms")) {
                System.out.printf("millis span: %s%n", commandLine.getOptionValue("ms"));
                millis_span = Integer.parseInt(commandLine.getOptionValue("ms"));
            }
        } catch (ParseException exception) {
            System.out.print("Parse error: ");
            System.out.println(exception.getMessage());
        }

        try (
                Cluster cluster = Cluster.connect(
                        ip,
                        ClusterOptions.clusterOptions(username, password)
                )
        ) {

            cluster.waitUntilReady(Duration.ofSeconds(10));


            ReactiveBucket bucket = cluster.bucket(bucketName).reactive();
            ReactiveScope scope = bucket.scope(scopeName);
            ReactiveCollection collection = scope.collection(collectionName);
            Map<Long, Double> lastValues = new Hashtable<>();
            Map<Long, Integer> sensorNames = new Hashtable<>();
            AtomicInteger counter = new AtomicInteger(0);


            Runnable insertScheduled = () -> {
                long startingTimestamp = 1682899200000L;
                long finalIntervals = 6307200;
                AtomicLong lastInterval = new AtomicLong();

                int buffer = 100;
                Flux.generate(() -> 0L, (i, sink) ->
                        {
                            sink.next(i);
                            if (i > finalIntervals) {
                                sink.complete();
                            }
                            return i + 1;
                        })
                        .buffer(buffer)
                        .map(countList -> Flux.fromIterable(countList)
                                .map(count ->

                                        {
                                            long currentThread = Thread.currentThread().getId();
                                            if(!sensorNames.containsKey(currentThread)){
                                                sensorNames.put(currentThread, counter.getAndIncrement());
                                            }
                                            Double lastValue = lastValues.get(currentThread);
                                            long millis = startingTimestamp + (lastInterval.get() * 5000);
                                            lastInterval.addAndGet(1);
                                            String id = sensorNames.get(currentThread) + ":" + millis / 3600000;
                                            double temperature = docGenerator.generateTemperature(lastValue);
                                            lastValues.put(currentThread, temperature);
                                            try {
                                                return
                                                        collection.mutateIn(id,
                                                                Collections.singletonList(arrayAppend("ts_data", List.of(Arrays.asList(millis, temperature))))).block();
                                            } catch (DocumentNotFoundException ex){
                                                return collection.upsert(
                                                        id,
                                                        docGenerator.generateDoc(millis, lastValue, sensorNames.get(currentThread), temperature)
                                                ).block();
                                            }

                                        }
                                )
                                .retry()
                                .collectList()
                                .block()
                        )
                        .retry()
                        .collectList()
                        .block();

            };


            ExecutorService ses = Executors.newFixedThreadPool(sensors);
            for(int i = 0; i < sensors; i++) {
                ses.execute(insertScheduled);
            }

            boolean error = ses.awaitTermination(maxTime == 0 ? Integer.MAX_VALUE : maxTime, TimeUnit.SECONDS);
            ses.shutdown();
            System.out.println(error ? "finished without errors" : "finished with errors");






        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}