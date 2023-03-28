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
    public static void writeFloatArray(BufferedWriter overallWriter, float[] arr) {
        try {
            for (float i : arr) overallWriter.write(String.valueOf(i) + " ");
            overallWriter.write("\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void writeFloatArrayList(BufferedWriter overallWriter, ArrayList<Float> arr) {
        try {
            for (float i : arr) overallWriter.write(String.valueOf(i) + " ");
            overallWriter.write("\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        try {
            String overallRes = "./reqcount_results/overall-results-future-2.txt";
            BufferedWriter overallWriter = new BufferedWriter(new FileWriter(overallRes));
            // int[] thresholds = new int[] {-1, 0, 1, 2, 3, 4, 5, 10, 50};
            int[] epoch_lengths = new int[] { -1, 1080000, 540000, 270000, 135000, 67500, 33750, 16875, 8437, 4218, 2109, 1054, 528, 264 };
            // int[] epoch_lengths = new int[] { -1, 4218, 2109, 1054, 527 };
            // int[] epoch_lengths = new int[] { -1, 2109 };
            // int[] epoch_lengths = new int[] { -1, 540000, 270000 };
            
            int[] times_highest = new int[epoch_lengths.length];
            float[] distFromHighest = new float[epoch_lengths.length];
            
            ArrayList<ArrayList<Float>> hitrates_per_trace = new ArrayList<>();
            for (int i = 1; i < 51; i++) {
                ArrayList<Float> param_hitrates = new ArrayList<>();

                // run trace i using each of the thresholds
                String traceStringNum = i < 10 ? "0" + Integer.toString(i) : Integer.toString(i);
                System.out.println("processing " + traceStringNum);
                String tracelink = "/mntData2/jason/cphy/w" + traceStringNum + ".oracleGeneral.bin";
                String trash = "./trash.txt";
                
                for (int j = 0; j < epoch_lengths.length; j++) {
                    System.out.println(String.format("epoch_lengths: %d", epoch_lengths[j]));
                    if (j == 0) {
                        // run the static alloc
                        CacheSimulatorStatic.LINES_READ_PER_CHUNK = 540000;
                        CacheSimulatorStatic currCache = new CacheSimulatorStatic(tracelink, trash, initSlabAlloc());
                        param_hitrates.add(currCache.calculateHitRate());
                    } else {
                        // run the request-count alloc
                        RequestCountAllocFuture.LINES_READ_PER_CHUNK = epoch_lengths[j];
                        RequestCountAllocFuture alloc = new RequestCountAllocFuture(tracelink, trash);
                        ArrayList<HashMap<Integer, Integer>> slabAlloc = alloc.processTrace();

                        // simulate the cache
                        CacheSimDynamic.LINES_READ_PER_CHUNK = epoch_lengths[j];
                        CacheSimDynamic currCache = new CacheSimDynamic(tracelink, trash, slabAlloc);
                        param_hitrates.add(currCache.calculateHitRate());
                                              
                        // RequestCountAlloc.LINES_READ_PER_CHUNK = epoch_lengths[j];
                        // RequestCountAlloc alloc = new RequestCountAlloc(tracelink, trash);
                        // param_hitrates.add(alloc.processTrace());
                    }
                }
                
                // increment counters for the epochs that created the max
                ArrayList<Integer> maxparams = new ArrayList<>();
                float max = -1;
                for (int j = 0; j < param_hitrates.size(); j++) {
                    if (param_hitrates.get(j) > max) {
                        max = param_hitrates.get(j);
                        maxparams.clear();
                        maxparams.add(j);
                    } else if (param_hitrates.get(j) == max) {
                        maxparams.add(j);
                    } 
                }
                for (int j : maxparams) {
                    times_highest[j]++;
                }            
                for (int j = 0; j < param_hitrates.size(); j++) {
                    distFromHighest[j] += Math.abs(max - param_hitrates.get(j));
                }
                hitrates_per_trace.add(param_hitrates);
            }
            for (int i = 0; i < distFromHighest.length; i++) distFromHighest[i] = distFromHighest[i] / 50;

            // dump the list of hitrates, as well as the final results for highest
            overallWriter.write("overall results: \n");
            writeIntArray(overallWriter, epoch_lengths);
            writeIntArray(overallWriter, times_highest);
            writeFloatArray(overallWriter, distFromHighest);
            overallWriter.write("\nhitrates per trace: \n");
            for (ArrayList<Float> hitrates : hitrates_per_trace) {
                writeFloatArrayList(overallWriter, hitrates);
            }
            overallWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
