import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.io.FileWriter;

public class NaiveBayes {
    private static int observations; // total number of observations
    private static int numAttributes; // number of attributes
    private static ArrayList<Set<String>> numAttributeValues; // number of possible values for each attribute
    private static HashMap<String, Integer> attributeCount; // counts of each attribute
    private static HashMap<String, Integer> classCount; // counts of each class
    private static HashMap<String, Double> priorProb; // prior probability for each class
    private static HashMap<String, HashMap<String, Integer>> jointCount; // counts of attribute-class pair
    private static HashMap<String, HashMap<String, Double>> evidenceLikelihood; // likelihood of evidence / conditional probs

    /**
     * Create a new NaiveBayes classifier
     */
    public NaiveBayes(){
        observations = 0;
        numAttributes = 0;
        numAttributeValues = new ArrayList<>();
        attributeCount = new HashMap<>();
        classCount = new HashMap<>();
        priorProb = new HashMap<>();
        jointCount = new HashMap<>();
        evidenceLikelihood = new HashMap<>();
    }

    /**
     * Reads CSV file, stores every line in an ArrayList
     * @param file
     * @return ArrayList of instances
     */
    public static Data readFile(String file){
        Data data = new Data(file);
        return data;
    }

    /**
     * Splits data set into a training-testing pair where trainingRatio represents ratio of data reserved for training
     * @param data
     * @param trainingRatio
     * @return
     */
    public static ArrayList<Data> trainTestSplit(Data data, double trainingRatio){
        ArrayList<Data> out = new ArrayList<>();
        Data copy = data.deepCopy();
        Data training = new Data();
        int trainingSize = (int)(data.size()*trainingRatio);
        while (training.size() < trainingSize){
            int index = new Random().nextInt(copy.size());
            training.add(copy.remove(index));
        }
        out.add(training);
        out.add(copy);
        return out;
    }
    /**
     * Splits data into k folds, each of these k folds becomes the testing set while remaining (k-1) folds are reserved for training
     * @param data
     * @param kfolds
     * @return
     */
    public static ArrayList<ArrayList<Data>> crossValidationSplit(Data data, int kfolds) {
        ArrayList<Data> folds = new ArrayList<>();
        Data copy = data.deepCopy();
        int foldSize = data.size() / kfolds;
        for (int i = 0; i < kfolds; i++) {
            Data fold = new Data();
            while (fold.size() < foldSize) {
                int index = new Random().nextInt(copy.size());
                fold.add(copy.remove(index));
            }
            folds.add(fold);
        }

        ArrayList<ArrayList<Data>> pairs = new ArrayList<>(); // training-testing pairs
        for (int i = 0; i < kfolds; i++) {
            ArrayList<Data> pair = new ArrayList<>();
            Data test = folds.get(i); // get each of the fold as testing set
            Data train = new Data();
            for (int j = 0; j < kfolds; j++) {
                if (j != i) {
                    train.addAll(folds.get(j));
                }
            }
            pair.add(train);
            pair.add(test);
            pairs.add(pair);
        }
        return pairs;
    }
    /**
     * Build the classifier. Keep counts of occurrences of each class, each attribute, each class-attribute pair
     * @param data
     */
    public static void buildClassifier(Data data){
        observations = data.size();
        numAttributes = data.numAttributes;
        for (Instance instance: data.instances) {
            String category = instance.actual;
            classCount.put(category, classCount.getOrDefault(category, 0) + 1);
            String[] attributes = instance.attributes;
            for (int i = 0; i < numAttributes; i++) {
                attributeCount.put(attributes[i], attributeCount.getOrDefault(attributes[i], 0) + 1);
                if (i < numAttributeValues.size()) {
                    numAttributeValues.get(i).add(attributes[i]);
                } else {
                    Set<String> valueCounts = new HashSet<>();
                    valueCounts.add(attributes[i]);
                    numAttributeValues.add(valueCounts);
                }

                if (!jointCount.containsKey(attributes[i])) {
                    HashMap<String, Integer> classWithCount = new HashMap<>();
                    classWithCount.put(category, 1);
                    jointCount.put(attributes[i], classWithCount);
                } else {
                    HashMap<String, Integer> classWithCount = jointCount.get(attributes[i]);
                    classWithCount.put(category, classWithCount.getOrDefault(category, 0) + 1);
                    jointCount.put(attributes[i], classWithCount);
                }
            }
        }
    }
    /**
     * Train the classifier. Calculate class prior probability (e.g. P(Obfuscate)),
     * likelihood of evidence (e.g. P(Location | Obfuscate))
     */
    public static void train(){
        // calculate class probabilities
        for (Map.Entry<String, Integer> entry: classCount.entrySet()){
            String category = entry.getKey();
            int count = entry.getValue();
            Double prob = Math.log((double)count/observations);
            priorProb.put(category,prob);
        }

        // calculate likelihood of evidence
        for (String category: priorProb.keySet()){
            for (Map.Entry<String, HashMap<String,Integer>> pair: jointCount.entrySet()){
                String attribute = pair.getKey();
                HashMap<String, Integer> attributeClassCounts = pair.getValue();
                int count = attributeClassCounts.getOrDefault(category,0);
                int numAttributeValue = 0;
                for (Set<String> set: numAttributeValues){
                    if (set.contains(attribute)){
                        numAttributeValue = set.size();
                    }
                }
                // laplace smoothing, handle cases where likelihood = 0
                Double prob = Math.log((double)(count+1.0)/(classCount.get(category)+numAttributeValue));
                if (!evidenceLikelihood.containsKey(attribute)){
                    evidenceLikelihood.put(attribute,new HashMap<>());
                }
                evidenceLikelihood.get(attribute).put(category,prob);
            }
        }
    }
    /**
     * Predict. Calculate class posterior class probability (e.g. P(Obfuscate | Location, First Party))
     * @param newInstance
     * @return Class with the highest posterior probability
     */
    public static String predict(Instance newInstance){
        Double maxProb = Double.NEGATIVE_INFINITY;
        String result = null;
        for (String category: classCount.keySet()) {
            Double prob = priorProb.get(category);
            for (String attribute : newInstance.attributes) {
                if (!evidenceLikelihood.containsKey(attribute)){
                    continue;
                }
                Double likelihood = evidenceLikelihood.get(attribute).get(category);
                prob += likelihood;
            }
            if (prob > maxProb){
                maxProb = prob;
                result = category;
            }
        }
        newInstance.predicted = result;
        return result;
    }
    /**
     * Calculate the accuracy of the prediction by comparing the value predicted by the model and the actual value.
     * @param testing
     * @return #true guesses / #instances
     */
    public static double calculateAccuracy(Data testing){
        int count = 0;
        for (Instance instance: testing.instances){
	    //System.out.println("Instance: " + instance.toString());
            String predicted = predict(instance);
	    //System.out.println("Actual: " + instance.actual + " Predicted: " +predicted); 
            if (instance.actual.equals(predicted)){
                count++;
            }
        }

        return 100*(double)count/testing.size();
    }
    public static void main(String args[]){
Scanner reader = new Scanner(System.in);
        System.out.print("Enter data file name (e.g. weather.csv): ");
        Data data = readFile(reader.next());
        System.out.print("Enter 1 if you want to do train-test split, 2 if you want to do k-fold cross validation split: ");
        int choice = reader.nextInt();
        if (choice == 1){
            System.out.print("Enter the ratio of data reserved for training (e.g. 0.8): ");
            Double ratio = reader.nextDouble();
            ArrayList<Data> pair = trainTestSplit(data,ratio);
            NaiveBayes nb = new NaiveBayes();
            nb.buildClassifier(pair.get(0));
            nb.train();
            Double accuracy = nb.calculateAccuracy(pair.get(1));
	    System.out.println("Data size: " + data.size());
            System.out.println("Accuracy: " + String.format("%.2f%%", accuracy));
        }
        if (choice == 2){
            System.out.print("Enter the number of folds (e.g. 5): " );
            int kfolds = reader.nextInt();
            ArrayList<ArrayList<Data>> pairs = crossValidationSplit(data,kfolds);
            ArrayList<Double> accuracies = new ArrayList<>();
            double accuracySum = 0;
            for (ArrayList<Data> pair: pairs){
                NaiveBayes nb = new NaiveBayes();
                Data training = pair.get(0);
                Data testing = pair.get(1);
                nb.buildClassifier(training);
                nb.train();
                double accuracy = calculateAccuracy(testing);
                accuracySum += accuracy;
                accuracies.add(accuracy);
            }
	    System.out.println("Data size: " + data.size());
            System.out.println("Accuracy of " + kfolds +" folds: " + accuracies);
            System.out.println("Average accuracy: " + String.format("%.2f%%", accuracySum/kfolds));
        }
	
        
    }
}

