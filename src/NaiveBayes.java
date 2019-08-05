import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class NaiveBayes {
    private static int observations; // total number of observations
    private static int numAttributes; // number of attributes
    private static ArrayList<String[]> data = new ArrayList<>();
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
    public static ArrayList<String[]> readFile(String file){
        BufferedReader br = null;
        String row = null;
        try{
            br = new BufferedReader(new FileReader(file));
            while ((row = br.readLine()) != null) {
                data.add(row.split(","));
            }
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }catch (IOException e){
            e.printStackTrace();
        }
        return data;
    }

    /**
     * Splits data set into a training-testing pair where trainingRatio represents ratio of data reserved for training
     * @param data
     * @param trainingRatio
     * @return
     */
    public static ArrayList<ArrayList<String[]>> trainTestSplit(ArrayList<String[]> data, double trainingRatio){
        ArrayList<ArrayList<String[]>> out = new ArrayList<>();
        ArrayList<String[]> copy = data;
        ArrayList<String[]> training = new ArrayList<>();
        int trainingSize = (int)(data.size()*trainingRatio);
        while (training.size() < trainingSize){
            int index = new Random().nextInt(copy.size());
            training.add(copy.remove(index));
        }
        out.add(training);
        System.out.println("Training size "+ training.size());
        out.add(copy);
        return out;
    }

    /**
     * Splits data into k folds, each of these k folds becomes the testing set while remaining (k-1) folds are reserved for training
     * @param data
     * @param kfolds
     * @return
     */
    public static ArrayList<ArrayList<ArrayList<String[]>>> crossValidationSplit(ArrayList<String[]> data, int kfolds) {
        ArrayList<ArrayList<String[]>> folds = new ArrayList<>();
        ArrayList<String[]> copy = data;
        int foldSize = data.size() / kfolds;
        for (int i = 0; i < kfolds; i++) {
            ArrayList<String[]> fold = new ArrayList<>();
            while (fold.size() < foldSize) {
                int index = new Random().nextInt(copy.size());
                fold.add(copy.remove(index));
            }
            folds.add(fold);
        }

        ArrayList<ArrayList<ArrayList<String[]>>> pairs = new ArrayList<>(); // training-testing pairs
        for (int i = 0; i < kfolds; i++) {
            ArrayList<ArrayList<String[]>> pair = new ArrayList<>();
            ArrayList<String[]> test = folds.get(i); // get each of the fold as testing set
            ArrayList<String[]> train = new ArrayList<>();
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
    public static void buildClassifier(ArrayList<String[]> data){
        for (String[] instance: data) {
            observations++;
            numAttributes = instance.length - 1;
            String category = instance[numAttributes];
            classCount.put(category, classCount.getOrDefault(category, 0) + 1);
            for (int i = 0; i < numAttributes; i++) {
                attributeCount.put(instance[i], attributeCount.getOrDefault(instance[i], 0) + 1);
                if (i < numAttributeValues.size()) {
                    numAttributeValues.get(i).add(instance[i]);
                } else {
                    Set<String> valueCounts = new HashSet<>();
                    valueCounts.add(instance[i]);
                    numAttributeValues.add(valueCounts);
                }

                if (!jointCount.containsKey(instance[i])) {
                    HashMap<String, Integer> classWithCount = new HashMap<>();
                    classWithCount.put(category, 1);
                    jointCount.put(instance[i], classWithCount);
                } else {
                    HashMap<String, Integer> classWithCount = jointCount.get(instance[i]);
                    classWithCount.put(category, classWithCount.getOrDefault(category, 0) + 1);
                    jointCount.put(instance[i], classWithCount);
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
    public static String predict(String[] newInstance){
        Double maxProb = Double.NEGATIVE_INFINITY;
        String result = null;
        for (String category: classCount.keySet()) {
            Double prob = priorProb.get(category);
            for (String attribute : newInstance) {
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
        return result;
    }

    /**
     * Return predictions for an arraylist of new instances
     * @param testing
     * @return
     */
    public static ArrayList<String> getPredictions(ArrayList<String[]> testing){
        ArrayList<String> predictions = new ArrayList<>();
        for (String[] instance: testing){
            String[] attributes = Arrays.copyOfRange(instance,0,instance.length-1);
            String actual = instance[instance.length-1];
            predictions.add(predict(attributes));
        }
        return predictions;
    }

    /**
     * Calculate the accuracy of the prediction by comparing the value predicted by the model and the actual value.
     * @param testing
     * @return #true guesses / #instances
     */
    public static double calculateAccuracy(ArrayList<String[]> testing){
        int count = 0;
        for (String[] instance: testing){
            String[] attributes = Arrays.copyOfRange(instance,0,instance.length-1);
            String actual = instance[instance.length-1];
            String predicted = predict(attributes);
            if (actual.equals(predicted)){
                count++;
            }
        }
        return (double)count/testing.size();
    }

    public static void main(String args[]){
        ArrayList<String[]> data = readFile("weather.csv");
        int kfolds= 7;
        ArrayList<ArrayList<ArrayList<String[]>>> pairs = crossValidationSplit(data,kfolds);
        ArrayList<Double> accuracies = new ArrayList<>();
        double accuracySum = 0;
        // train and calculate accuracy for each pair of training-testing sets
        for (ArrayList<ArrayList<String[]>> pair: pairs){
            NaiveBayes nb = new NaiveBayes();
            ArrayList<String[]> training = pair.get(0);
            ArrayList<String[]> testing = pair.get(1);
            nb.buildClassifier(training);
            nb.train();
            double accuracy = nb.calculateAccuracy(testing);
            accuracySum += accuracy;
            accuracies.add(accuracy);
        }
        System.out.println("Accuracy of " + kfolds +" folds: " + accuracies);
        System.out.println("Average accuracy: " + accuracySum/kfolds);
    }
}
