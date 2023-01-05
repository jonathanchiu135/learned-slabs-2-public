import java.util.*;
import java.io.*;

public class TestThresholdsDriver {
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

    /* 
     * Test 1: test threshold
     *  constant slab allocation as: 
     *  slabAllocation.put(64, 16);
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
     */

    public static void writeIntArray(BufferedWriter overallWriter, int[] arr) {
        for (int i : arr) overallWriter.write(String.valueOf(i) + " ");
        overallWriter.write("\n");
    }
    public static void writeFloatArray(BufferedWriter overallWriter, float[] arr) {
        for (float i : arr) overallWriter.write(String.valueOf(i) + " ");
        overallWriter.write("\n");
    }

    public static void main(String[] args) {
        try {
            String overallRes = "./parameters-test-thresholds/overallRes.txt";
            BufferedWriter overallWriter = new BufferedWriter(new FileWriter(overallRes));
            // SHOULD MAKE SURE THESE ARE REASONABLE - MAYBE TRY PRINTING SOME OUT
            // last index represents static trace
            int[] thresholds = new int[] {0, 50, 100, 200, 500, 1000, 2000, 5000, 10000};
            int[] times_highest = new int[thresholds.size + 1];
            ArrayList<float[]> hitrates_per_trace = new ArrayList<>();
            for (int i = 1; i < 51; i++) {
                ArrayList<Float> threshold_hitrates = new int[thresholds.size + 1];

                // run the traces
                String traceStringNum = i < 10 ? "0" + Integer.toString(i) : Integer.toString(i);
                System.out.println("processing " + traceStringNum);
                String tracelink = "/mntData2/jason/cphy/w" + traceStringNum + ".oracleGeneral.bin";
                String trash = "./trash.txt";

                for (int j = 0; j < thresholds.size; j++) {
                    AllocCostBenefit.MOVE_THRESHOLD = thresholds[j];
                    AllocCostBenefit alloc = new AllocCostBenefit(tracelink, trash);
                    threshold_hitrates.add(alloc.processTrace());
                }
                
                // run the static alloc
                CacheSimulatorStatic currCache = new CacheSimulatorStatic(tracelink, staticRes, initSlabAlloc());
                threshold_hitrates.add(currCache.calculateHitRate());
                
                // increment counters for the thresholds that created the max
                ArrayList<Integer> maxThresholds = new ArrayList<>();
                float max = -1;
                for (int j = 0; j < thresholds_hitrates.size(); j++) {
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
            
            overallWriter.write("hitrates per trace: \n");
            writeIntArray(overallWriter, times_highest);
            for (float[] hitrates : hitrates_per_trace) {
                writeFloatArray(overallWriter, hitrates);
            }
            overallWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

// move least used -> to most used
// to readjust (if not making a difference):
//  either move more often / move more slabs at once
//  play around with lines between recalculations
//  test using a threshhold for moving (instead of always moving)

