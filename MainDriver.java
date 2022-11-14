import java.util.*;
import java.io.*;

public class MainDriver {
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

    // trace1: cost-benefit
    // trace2: static
    public static void spliceResults(String trace1Res, String trace2Res, String resultLink, BufferedWriter overallWriter, int i) {
        BufferedReader reader1;
        BufferedReader reader2;
        BufferedWriter writer;

        try {
            reader1 = new BufferedReader(new FileReader(trace1Res));
            reader2 = new BufferedReader(new FileReader(trace2Res));
            writer = new BufferedWriter(new FileWriter(resultLink));

            float epoch1Hitrate, lifetime1Hitrate, epoch2Hitrate, lifetime2Hitrate;
            epoch1Hitrate = lifetime1Hitrate = epoch2Hitrate = lifetime2Hitrate = 0;

            String line1 = reader1.readLine();
            reader2.readLine();
            while (line1 != null) {
                String epochNum = line1.split(" ")[1];
                epoch1Hitrate = Float.valueOf(reader1.readLine().split(" ")[2]);
                lifetime1Hitrate = Float.valueOf(reader1.readLine().split(" ")[2]);
                reader1.readLine();
                line1 = reader1.readLine();

                epoch2Hitrate = Float.valueOf(reader2.readLine().split(" ")[2]);
                lifetime2Hitrate = Float.valueOf(reader2.readLine().split(" ")[2]);
                reader2.readLine();
                reader2.readLine();

                writer.write(epochNum+"\n");
                writer.write(String.format("epoch hitrate: %f vs. %f\n", epoch1Hitrate, epoch2Hitrate));
                writer.write(String.format("lifetime hitrate: %f vs. %f\n", lifetime1Hitrate, lifetime2Hitrate));
                writer.write("\n");
            }    

            overallWriter.write(String.format("%d lifetime hitrate: %f vs. %f\n", i, lifetime1Hitrate, lifetime2Hitrate));

            reader1.close();
            reader2.close();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return;
    }

    public static void main(String[] args) {
        try {
            String overallRes = "./results2/overallRes.txt";
            BufferedWriter overallWriter = new BufferedWriter(new FileWriter(overallRes));
            for (int i = 1; i < 51; i++) {
                String traceStringNum = i < 10 ? "0" + Integer.toString(i) : Integer.toString(i);

                // run the traces
                System.out.println("processing " + traceStringNum);
                String tracelink = "/mntData2/jason/cphy/w" + traceStringNum + ".oracleGeneral.bin";
                String costBenefitRes =  "./results2/w" + traceStringNum + ".cost-benefit.txt";
                String staticRes = "./results2/w" + traceStringNum + ".static-cache.txt";
                String splicedRes = "./results2/w" + traceStringNum + ".spliced.txt";
                
                // cost-benefit analysis
                AllocCostBenefit alloc = new AllocCostBenefit(tracelink, costBenefitRes);
                alloc.processTrace();

                // static cache
                HashMap<Integer, Integer> slabAllocation = initSlabAlloc();
                CacheSimulatorStatic currCache = new CacheSimulatorStatic(tracelink, staticRes, slabAllocation);
                currCache.calculateHitRate();

                // splice the results together
                spliceResults(costBenefitRes, staticRes, splicedRes, overallWriter, i);
            }
            overallWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}