package rule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import util.Constants;

public class MasterResponse implements Runnable {
	
	private ArrayList<Socket> slaveSocketList;
    private Map<Integer, Integer> freeSlaveMap;
    private Map<Integer, Integer> overloadMap;

	public MasterResponse(ArrayList<Socket> slaveSocketList) {
		this.slaveSocketList = slaveSocketList;
		this.freeSlaveMap  = new HashMap<Integer, Integer>();
		this.overloadMap  = new HashMap<Integer, Integer>();
	}

	@Override
	public void run() {
		try {
			while(true) {
			    int totalProcessNum = fillSlaveMap();

			    if (totalProcessNum > this.slaveSocketList.size() * Constants.CONN_MAX_PROCESS) {
			        System.out.println("The whole System is overloaded.");
			        continue;
			    }

			    //do load balance
			    Iterator<Entry<Integer, Integer>> ito = overloadMap.entrySet().iterator();
			    Entry<Integer, Integer> oEntry;
				while (ito.hasNext()){
				    oEntry = ito.next();
				    for (Entry<Integer, Integer> fEntry : freeSlaveMap.entrySet()) {
				        while (fEntry.getValue() > 0) {
				            if (oEntry.getValue() > 0) {
				                migrateProcess(slaveSocketList.get(oEntry.getKey()), slaveSocketList.get(fEntry.getKey()));
				                oEntry.setValue(oEntry.getValue()-1);
				                fEntry.setValue(fEntry.getValue()-1);
				            } else if (ito.hasNext()) {
				                oEntry = ito.next();
				            } else {
				                break;
				            }
				        }
				    }
				}
				Thread.sleep(Constants.CONN_POLL_INTERVAL);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	private int fillSlaveMap() throws NumberFormatException, IOException{
	    int totalProcessNum = 0;
	    
        for(int i = 0; i<slaveSocketList.size(); i++) {
            BufferedReader in = new BufferedReader(new InputStreamReader(slaveSocketList.get(i).getInputStream()));
            String response = in.readLine();
            if(response != null) {
                int processNum = Integer.parseInt(response); 
                if ( processNum > Constants.CONN_MAX_PROCESS) {
                    overloadMap.put(i, processNum - Constants.CONN_MAX_PROCESS);
                }
                else if (processNum < Constants.CONN_MAX_PROCESS){
                    freeSlaveMap.put(i, Constants.CONN_MAX_PROCESS - processNum);
                }
                totalProcessNum += processNum;
            } else {
                //can not get response, and max number
                totalProcessNum += Constants.CONN_MAX_PROCESS;
                System.out.println("Can not get process number from "+ i + "th slave");
            }
        }
        return totalProcessNum;
	}

	private void migrateProcess(Socket overloadSocket, Socket freeSocket) throws IOException {
        PrintWriter out = new PrintWriter(overloadSocket.getOutputStream(), true);
        out.println(Constants.CONN_LEAVE);
        BufferedReader in = new BufferedReader(new InputStreamReader(overloadSocket.getInputStream()));
        String serializedObj = in.readLine();
        PrintWriter emit = new PrintWriter(freeSocket.getOutputStream(), true);
        emit.println(serializedObj);
        emit.flush();
	}
}