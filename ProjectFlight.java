import java.io.IOException;
import java.util.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class ProjectFlight{

    public static class MapperOne extends Mapper<LongWritable, Text, Text, DoubleWritable>{
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] line = value.toString().split(",");

            // Checks the ArrDelay Column
            if (line[14].trim().matches("-?[0-9]+")) {
                
                int ArrDelay = Integer.valueOf(line[14]);
                
                // Ontime
                if (ArrDelay >= -15 && ArrDelay <= 15){
                    context.write(new Text(line[8].trim()), new DoubleWritable(1.0));
                }

                // Late
                else {
                    context.write(new Text(line[8].trim()), new DoubleWritable(0.0));
                }

            }
        }
    }

    public static class ReducerOne extends Reducer<Text, DoubleWritable, Text, DoubleWritable>{
        TreeMap<String, Double> onTimeMap = new TreeMap<>();
        public void reduce(Text key, Iterable<DoubleWritable> values, Context context) throws IOException, InterruptedException {
            long count = 0;
            long row = 0;
            for (DoubleWritable value : values) {
                count += value.get();
                row += 1;
            }

            double onTime = (double) count / row;

            onTimeMap.put(key.toString(), onTime);
        }

        protected void cleanup(Context context) throws IOException, InterruptedException {
            TreeMap<String, Double> sortedOnTimeMap = sortByValues(onTimeMap);

            int count = 0;
            for (Map.Entry<String, Double> entry : sortedOnTimeMap.entrySet()) {
                String position;
                if (count < 3) {
                    position = "Top 3";
                } else if (count >= sortedOnTimeMap.size() - 3) {
                    position = "Bottom 3";
                } else {
                    count++;
                    continue; 
                }

                context.write(new Text(entry.getKey() + " (" + position + ")"), new DoubleWritable(entry.getValue()));
                count++;
            }
        }
    }

    public static class MapperTwo extends Mapper<LongWritable, Text, Text, DoubleWritable> {
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] line = value.toString().split(",");

            // Taxi In & Origin
            if (line.length > 19 && line[19].trim().matches("-?[0-9]+") && !line[19].trim().equals("")) {
                context.write(new Text("Taxi In " + line[17]), new DoubleWritable(Double.parseDouble(line[19])));
            }

            // Taxi Out & Dest
            if (line.length > 19 && line[20].trim().matches("-?[0-9]+") && !line[19].trim().equals("")) {
                context.write(new Text("Taxi Out " + line[16]), new DoubleWritable(Double.parseDouble(line[20])));
            }
        }
    }

    public static class ReducerTwo extends Reducer<Text, DoubleWritable, Text, DoubleWritable> {
        TreeMap<String, Double> inAverageMap = new TreeMap<>();
        TreeMap<String, Double> outAverageMap = new TreeMap<>();

        public void reduce(Text key, Iterable<DoubleWritable> values, Context context) throws IOException, InterruptedException {
            double sum = 0.0;
            long count = 0;

            for (DoubleWritable value : values) {
                sum += value.get();
                count += 1;
            }

            double average = (count > 0) ? sum / count : 0.0;

            if (key.toString().startsWith("Taxi In")) {
                inAverageMap.put(key.toString(), average);
            } else if (key.toString().startsWith("Taxi Out")) {
                outAverageMap.put(key.toString(), average);
            }
        }

        protected void cleanup(Context context) throws IOException, InterruptedException {
            TreeMap<String, Double> sortedInAverageMap = sortByValues(inAverageMap);
            TreeMap<String, Double> sortedOutAverageMap = sortByValues(outAverageMap);

            writeTopAndBottom(context, sortedInAverageMap, "Taxi In");
            writeTopAndBottom(context, sortedOutAverageMap, "Taxi Out");
        }

        private void writeTopAndBottom(Context context, TreeMap<String, Double> sortedAverageMap, String direction) throws IOException, InterruptedException {
            int count = 0;
            for (Map.Entry<String, Double> entry : sortedAverageMap.entrySet()) {
                String position;
                if (count < 3) {
                    position = "Top 3";
                } else if (count >= sortedAverageMap.size() - 3) {
                    position = "Bottom 3";
                } else {
                    count++;
                    continue;
                }

                context.write(new Text(entry.getKey() + " (" + direction + " - " + position + ")"), new DoubleWritable(entry.getValue()));
                count++;
            }
        }
    }

    public static class MapperThree extends Mapper<LongWritable, Text, Text, LongWritable> {
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] line = value.toString().split(",");

            String cancelCode = line[22].trim();

            if (cancelCode != null && !cancelCode.equals("") && !cancelCode.equals("NA") && !cancelCode.equals("CancellationCode")) {
                context.write(new Text(line[22]), new LongWritable(1));
            }
        }
    }

    public static class ReducerThree extends Reducer<Text, LongWritable, Text, LongWritable> {
        TreeMap<String, Long> top5Map = new TreeMap<>();

        public void reduce(Text key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException {
            long count = 0;
            for (LongWritable value : values) {
                count += value.get();
            }
            top5Map.put(key.toString(), count);
        }

        protected void cleanup(Context context) throws IOException, InterruptedException {
            TreeMap<String, Long> sortedTop5Map = sortByValues(top5Map);

            for (Map.Entry<String, Long> entry : sortedTop5Map.entrySet()) {
                context.write(new Text(entry.getKey()), new LongWritable(entry.getValue()));
            }
        }
    }

    public static <K, V extends Comparable<V>> TreeMap<K, V> sortByValues(final TreeMap<K, V> map) {
        Comparator<K> valueComparator = new Comparator<K>() {
            public int compare(K k1, K k2) {
                int compare = map.get(k2).compareTo(map.get(k1));
                if (compare == 0) return 1;
                else return compare;
            }
        };
        TreeMap<K, V> sortedByValues = new TreeMap<K, V>(valueComparator);
        sortedByValues.putAll(map);
        return sortedByValues;
    }


    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();

        // Job 1
        Job job1 = Job.getInstance(conf, "ProjectFlight1");
        job1.setJarByClass(ProjectFlight.class);
        job1.setMapperClass(MapperOne.class);
        job1.setReducerClass(ReducerOne.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(DoubleWritable.class);
        FileInputFormat.addInputPath(job1, new Path(args[0]));
        FileOutputFormat.setOutputPath(job1, new Path(args[1] + "_output1"));

        if (job1.waitForCompletion(true)) {
            // Do not use output1 as input for job2, use the original input file
            // Job 2
            Job job2 = Job.getInstance(conf, "ProjectFlight2");
            job2.setJarByClass(ProjectFlight.class);
            job2.setMapperClass(MapperTwo.class);
            job2.setReducerClass(ReducerTwo.class);
            job2.setOutputKeyClass(Text.class);
            job2.setOutputValueClass(DoubleWritable.class);
            FileInputFormat.addInputPath(job2, new Path(args[0]));  // Use the original input file
            FileOutputFormat.setOutputPath(job2, new Path(args[1] + "_output2"));

            if (job2.waitForCompletion(true)) {
                // Do not use output2 as input for job3, use the original input file
                // Job 3
                Job job3 = Job.getInstance(conf, "ProjectFlight3");
                job3.setJarByClass(ProjectFlight.class);
                job3.setMapperClass(MapperThree.class);
                job3.setReducerClass(ReducerThree.class);
                job3.setOutputKeyClass(Text.class);
                job3.setOutputValueClass(LongWritable.class);
                FileInputFormat.addInputPath(job3, new Path(args[0]));  // Use the original input file
                FileOutputFormat.setOutputPath(job3, new Path(args[1] + "_output3"));

                System.exit(job3.waitForCompletion(true) ? 0 : 1);
            }
        }
    }


}

