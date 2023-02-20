/* 
    Driver code to test the request-counting-allocation strategy on different traces

    Expects CacheSimulatorStatic and AllocCostBenefit to NOT write to any result file 
        should instead just return the FINAL hitrate for the entire trace

    Writes to result file "./reqcount_results/overall-results.txt" in following format:
    [
        overall results:
        -1 0 1 2 3 4 5 10 20 50                      (array of threshold multipliers; -1 represents "static")
        18 39 18 18 23 1 ...                           (the array detailing how many times each threshold was the highest)

        hitrates per trace 
        .2 .3 .4123 .1 .123 ...                     (the hitrates on trace 1)
        .1 .4 .2 .11131 .123 ...                    (the hitrates on trace 2)
        .2 .3 .4 .1 .2 ...
        ...
    ]

    increase number of slabs hitrate should be ~50%
    actually use future info (instead of past)

    track number of migrations per threshold / plot values to see if higher (box plot)

    test with shorter epochs
        can actually see the effect of the cost (longer is less)
        and after that we can accoutn for penaly
*/
import java.util.*;
import java.io.*;

public class RequestCountDriver {
    public static HashMap<Integer, Integer> initSlabAlloc() {
        HashMap<Integer, Integer> slabAllocation = new HashMap<>();
        slabAllocation.put(64, 16);
        slabAllocation.put(128, 8);
        slabAllocation.put(256, 8);
        slabAllocation.put(512, 4);
        slabAllocation.put(1024, 4);
        slabAllocation.put(2048, 3);
        slabAllocation.put(4096, 3);
        slabAllocation.put(8192, 2);
        slabAllocation.put(16384, 2);
        slabAllocation.put(32768, 2);
        slabAllocation.put(65536, 1);
        slabAllocation.put(131072, 1);
        slabAllocation.put(262144, 1);
        slabAllocation.put(524288, 1);
        slabAllocation.put(1048576, 1);
        return slabAllocation;
    }
    public static void writeIntArray(BufferedWriter overallWriter, int[] arr) {
        try {
            for (int i : arr) overallWriter.write(String.valueOf(i) + " ");
            overallWriter.write("\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void writeFloatArray(BufferedWriter overallWriter, ArrayList<Float> arr) {
        try {
            for (float i : arr) overallWriter.write(String.valueOf(i) + " ");
            overallWriter.write("\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        try {
            String overallRes = "./reqcount_results/overall-results.txt";
            BufferedWriter overallWriter = new BufferedWriter(new FileWriter(overallRes));
            int[] thresholds = new int[] {-1, 0, 1, 2, 3, 4, 5, 10, 50};
            int[] times_highest = new int[thresholds.length];
            ArrayList<ArrayList<Float>> hitrates_per_trace = new ArrayList<>();
            for (int i = 1; i < 51; i++) {
                ArrayList<Float> threshold_hitrates = new ArrayList<>();

                // run trace i using each of the thresholds
                String traceStringNum = i < 10 ? "0" + Integer.toString(i) : Integer.toString(i);
                System.out.println("processing " + traceStringNum);
                String tracelink = "/mntData2/jason/cphy/w" + traceStringNum + ".oracleGeneral.bin";
                String trash = "./trash.txt";
                
                for (int j = 0; j < thresholds.length; j++) {
                    System.out.println(String.format("threshold: %d", thresholds[j]));
                    if (j == 0) {
                        // run the static alloc
                        CacheSimulatorStatic currCache = new CacheSimulatorStatic(tracelink, trash, initSlabAlloc());
                        threshold_hitrates.add(currCache.calculateHitRate());
                    } else {
                        // run the request-count alloc
                        RequestCountAlloc.THRESHOLD_MULTIPLIER = thresholds[j];
                        RequestCountAlloc alloc = new RequestCountAlloc(tracelink, trash);
                        threshold_hitrates.add(alloc.processTrace());
                    }
                }
                
                // increment counters for the thresholds that created the max
                ArrayList<Integer> maxThresholds = new ArrayList<>();
                float max = -1;
                for (int j = 0; j < threshold_hitrates.size(); j++) {
                    if (threshold_hitrates.get(j) > max) {
                        max = threshold_hitrates.get(j);
                        maxThresholds.clear();
                        maxThresholds.add(j);
                    } else if (threshold_hitrates.get(j) == max) {
                        maxThresholds.add(j);
                    } 
                }
                for (int j : maxThresholds) {
                    times_highest[j]++;
                }            
                hitrates_per_trace.add(threshold_hitrates);
            }

            // dump the list of hitrates, as well as the final results for highest
            overallWriter.write("overall results: \n");
            writeIntArray(overallWriter, thresholds);
            writeIntArray(overallWriter, times_highest);
            overallWriter.write("\nhitrates per trace: \n");
            for (ArrayList<Float> hitrates : hitrates_per_trace) {
                writeFloatArray(overallWriter, hitrates);
            }
            overallWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
