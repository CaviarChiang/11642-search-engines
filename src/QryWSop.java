import java.util.ArrayList;
import java.util.List;

public abstract class QryWSop extends QrySop {

    List<Double> weights = new ArrayList<>();

    public void setWeights(List<Double> weights) {
        this.weights.addAll(weights);
    }

    public void addWeight(double weight) {
        weights.add(weight);
    }

    public List<Double> getWeights() {
        return new ArrayList<>(weights);
    }

    public double getTotalWeight() {
        double total = 0;
        for (Double w : weights) total += w;
        return total;
    }

    @Override
    public String toString() {

        StringBuilder result = new StringBuilder();

        for (Qry arg : this.args)
            result.append(arg).append(" ");
        result = new StringBuilder(this.getDisplayName() + "( " + result + " Weights: ");
        for (Double weight : this.weights)
            result.append(weight).append(" ");
        result.append(")");

        return result.toString();

    }
}
