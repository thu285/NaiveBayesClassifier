import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Data {

    List<Instance> instances; // list of instances
    int numAttributes; // number of attributes

    /**
     * Create a new Data object given CSV file
     * @param file
     */
    public Data(String file){
        instances = new ArrayList<>();
        BufferedReader br = null;
        String row = null;
        try{
            br = new BufferedReader(new FileReader(file));
            while ((row = br.readLine()) != null) {
                String[] rowList = row.split(",");
                numAttributes = rowList.length-1;
                String category = rowList[numAttributes];
                String[] attributes = Arrays.copyOfRange(rowList,0,numAttributes);
                Instance i = new Instance(attributes, category);
                instances.add(i);
            }

        }catch (FileNotFoundException e){
            e.printStackTrace();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * Create a new empty Data object
     */
    public Data(){
        instances = new ArrayList<>();
    }

    /**
     * Returns the number of instances in Data object
     */
    int size(){
        return instances.size();
    }

    /**
     * Add a new instance in Data object
     * @param instance
     */
    void add(Instance instance){
        instances.add(instance);
    }

    /**
     * Add all instances of another Data object
     * @param data
     */
    void addAll(Data data){
        for (Instance i: data.instances){
            add(i);
        }
    }

    /**
     * Remove and return an Instance at given index
     * @param index
     */
    Instance remove(int index){
        Instance instance = instances.remove(index);
        return instance;
    }

    /**
     * Return String representation of Data object which is a list of Instance objects
     */
    public String toString(){
        return instances.toString();
    }
}
