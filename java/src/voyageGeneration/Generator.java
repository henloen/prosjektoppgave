package voyageGeneration;

import java.util.ArrayList;
import java.util.Arrays;

public class Generator {
	
	private ArrayList<Label> unexploredLabels, exploredLabels;
	private Installation[] installationSubset;
	private int[] installationNumbers;
	private Vessel vessel;
	private int maxDuration;

	public Generator(Installation[] installationSubset, Vessel vessel, int maxDuration) {
		this.vessel = vessel;
		this.maxDuration = maxDuration;
		unexploredLabels= new ArrayList<Label>();
		exploredLabels= new ArrayList<Label>();
		this.installationSubset = installationSubset;
		int[] visited = new int[installationSubset.length]; // initial list of visited installations, the index represents the sequence of visits
		Arrays.fill(visited, -1);
		visited[0] = 0;
		Label initLabel = new Label(0, 16,0, installationSubset[0],visited); //first and last installation in the installationSet is the depot
		unexploredLabels.add(initLabel);
		createInstallationNumbers();
	}

	public Label findCheapestVoyage() {
		while(! unexploredLabels.isEmpty()) {
			extendLabel(unexploredLabels.get(0));
		}
		Label cheapestLabel = null;
		double lowestCost = Integer.MAX_VALUE; //starting value
		for (Label exploredLabel : exploredLabels) {
			if (exploredLabel.getCost() < lowestCost && isGoalLabel(exploredLabel)) {
				cheapestLabel = exploredLabel;
				lowestCost = exploredLabel.getCost();
			}
		}
		return cheapestLabel; //returns null if there is no feasible solution to the subproblem
	}
	
	private boolean isGoalLabel (Label label) {
		return (label.getCurrentInstallation() == installationSubset[0]
				&& getPossibleInstallations(label.getVisited()).isEmpty()); 
	}
	
	private void extendLabel(Label currentLabel) {
		ArrayList<Installation> possibleInstallations = getPossibleInstallations(currentLabel.getVisited());
		if (possibleInstallations.isEmpty()){
			Label newLabel = extendLabelToDepot(currentLabel);
			if (! (newLabel == null)) {
				dominateLabels(newLabel);
			}
		}
		else{
			for (Installation installation : possibleInstallations) {
				Label newLabel = extendLabelToInstallation(currentLabel,installation);
				if (newLabel == null) {
					continue;
				}
				dominateLabels(newLabel);
			}
		}
		unexploredLabels.remove(currentLabel);
		exploredLabels.add(currentLabel);			
	}
	
	private Label extendLabelToDepot(Label currentLabel) {
		Installation currentInstallation = currentLabel.getCurrentInstallation();
		Installation depot = installationSubset[0];
		Label newLabel;
		int currentDepartureTime = currentLabel.getDepartureTime();
		double currentCost = currentLabel.getCost();
		int sailingTime = (int) Math.ceil((Utility.getDistance(currentInstallation, depot)/vessel.getSpeed()));
		int arrivalTime = currentDepartureTime + sailingTime;
		int todaysOpeningHour = depot.getTodaysOpeningHour(arrivalTime);
		int timeVoyageFinished;
		if (arrivalTime <= todaysOpeningHour) {
			timeVoyageFinished = todaysOpeningHour;
		}
		else { //assume that the depot opens at the 
			timeVoyageFinished = todaysOpeningHour + 24; 
		}
		if (timeVoyageFinished <= maxDuration) {
			double finalCost = currentCost + (sailingTime*vessel.getFuelCostSailing());
			newLabel = new Label(finalCost,timeVoyageFinished,currentLabel.getCapacityUsed(),depot,currentLabel.getVisited());
		}
		else {
			newLabel = null;
		}
		return newLabel;
		
	}
	
	private Label extendLabelToInstallation(Label currentLabel, Installation nextInstallation) {
		Installation currentInstallation = currentLabel.getCurrentInstallation();
		Label newLabel;
		int currentDepartureTime = currentLabel.getDepartureTime();
		double currentCost = currentLabel.getCost();
		int sailingTime = (int) Math.ceil((Utility.getDistance(currentInstallation, nextInstallation)/vessel.getSpeed()));
		int arrivalTime = currentDepartureTime + sailingTime;
		int todaysOpeningHour = nextInstallation.getTodaysOpeningHour(arrivalTime);
		System.out.println("Extending from installation " + currentInstallation.getName() + " to " + nextInstallation.getName());
		int todaysClosingHour = nextInstallation.getTodaysClosingHour(arrivalTime);
//		System.out.println(todaysClosingHour);
		int waitingTime = 0;
		int tomorrowsOpeningHour = todaysOpeningHour+24; //assumes same opening hours every day
		if (arrivalTime < todaysOpeningHour) {
			waitingTime = todaysOpeningHour - arrivalTime;
		}
		else if (arrivalTime >= todaysClosingHour) {
			waitingTime = tomorrowsOpeningHour - arrivalTime;
		}
		//assumption: no installations have a service time greater than one working day
		else if (arrivalTime + nextInstallation.getServiceTime() > todaysClosingHour) {
			waitingTime = tomorrowsOpeningHour - todaysClosingHour; //add a night
		}
		int nextDepartureTime = arrivalTime + nextInstallation.getServiceTime() + waitingTime;
		double capacityUsed = currentLabel.getCapacityUsed();
		double nextCapacityUsed = capacityUsed + nextInstallation.getDemandPerVisit();
		//check that the solution is feasible
		if (nextDepartureTime <= maxDuration && nextCapacityUsed <= vessel.getCapacity()) {
			double nextCost = currentCost + (sailingTime*vessel.getFuelCostSailing()) + ((waitingTime+nextInstallation.getServiceTime())*vessel.getFuelCostInstallation());
			newLabel = new Label(nextCost,nextDepartureTime,nextCapacityUsed,nextInstallation,getNextVisited(currentLabel, nextInstallation));
		}
		else {
			newLabel = null;
		}

//		System.out.println("waiting time:" + waitingTime);
//		System.out.println("arrivalTime:" + arrivalTime);
//		System.out.println("todaysClosingHour:" + todaysClosingHour);
//		System.out.println("tommorrowsOpeningHour:" + tomorrowsOpeningHour);
		return newLabel;
	}
	
	private void dominateLabels(Label newLabel) {
		for (Label unexploredLabel : unexploredLabels) {
			if (dominates(unexploredLabel,newLabel)){
				return;
			}
		}
		for (Label exploredLabel: exploredLabels) {
			if (dominates(exploredLabel,newLabel)){
				return;
			}
		}
		//add the label to the list of unexplored labels if it's not dominated by any existing labels
		unexploredLabels.add(newLabel);
		for (Label unexploredLabel : unexploredLabels) {
			if (dominates(newLabel, unexploredLabel)){
				unexploredLabels.remove(unexploredLabel);
			}
		}
		for (Label exploredLabel : exploredLabels) {
			if (dominates(newLabel, exploredLabel)){
				exploredLabels.remove(exploredLabel);
			} 
		}
		
	}
	
	//true if label1 dominates label2
	private boolean dominates (Label label1, Label label2) {
		//a label can't dominate itself
		if (label1 == label2) {
			return false;
		}
		if (label1.getCurrentInstallation() == label2.getCurrentInstallation()
				&& visitedSameInstallations(label1,label2)
				&& label1.getCost() <= label2.getCost()
				&& label1.getDepartureTime() <= label2.getDepartureTime()) {
			return true;
		}
		return false;
	}
	
	private boolean visitedSameInstallations(Label label1, Label label2) {
		int[] installationList1 = Arrays.copyOf(label1.getVisited(), label1.getVisited().length);
		int[] installationList2 = Arrays.copyOf(label2.getVisited(), label2.getVisited().length);
		Arrays.sort(installationList1);
		Arrays.sort(installationList2);
		return Arrays.equals(installationList1, installationList2);
	}
	
	private int[] getNextVisited(Label currentLabel, Installation nextInstallation) {
		int[] nextVisited = Arrays.copyOf(currentLabel.getVisited(), currentLabel.getVisited().length);
		for (int i=0; i<nextVisited.length;i++) {
			if (nextVisited[i] == -1) {
				nextVisited[i] = nextInstallation.getNumber();
				return nextVisited;
			}
		}
		return nextVisited;
	}
	
	private ArrayList<Installation> getPossibleInstallations(int[] visited) {
		ArrayList<Installation> possibleInstallations = new ArrayList<Installation>();
		// start at 1 because depot is 0 and should not be visited before last step
		for (int i=1; i<installationNumbers.length;i++) {
			boolean hasBeenVisited = false;
			for (int j=0;j<visited.length;j++) {
				if (installationNumbers[i] == visited[j]) {
					hasBeenVisited = true;
					break;
				}
			}
			if (!hasBeenVisited) {
				possibleInstallations.add(installationSubset[i]);
			}
		}
		return possibleInstallations;
	}
	
	private void createInstallationNumbers() {
		installationNumbers = new int[installationSubset.length];
		for (int i=0;i<installationSubset.length;i++) {
			installationNumbers[i] = installationSubset[i].getNumber();
		}
	}
}