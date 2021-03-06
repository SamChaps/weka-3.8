/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 *    FURIA.java
 *    Copyright (C) 2008,2009 Jens Christian Huehn
 *    
 *    (based upon) JRip.java
 *    Copyright (C) 2001 Xin Xu, Eibe Frank
 */

package weka.classifiers.rules;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;

import weka.classifiers.AbstractClassifier;
import weka.core.AdditionalMeasureProducer;
import weka.core.Attribute;
import weka.core.Capabilities;
import weka.core.Capabilities.Capability;
import weka.core.Copyable;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.SelectedTag;
import weka.core.Tag;
import weka.core.TechnicalInformation;
import weka.core.TechnicalInformation.Field;
import weka.core.TechnicalInformation.Type;
import weka.core.TechnicalInformationHandler;
import weka.core.Utils;
import weka.core.WeightedInstancesHandler;

/**
 * <!-- globalinfo-start --> FURIA: Fuzzy Unordered Rule Induction Algorithm<br/>
 * <br/>
 * Details please see:<br/>
 * <br/>
 * Jens Christian Huehn, Eyke Huellermeier (2009). FURIA: An Algorithm for
 * Unordered Fuzzy Rule Induction. Data Mining and Knowledge Discovery..<br/>
 * <br/>
 * <p/>
 * <!-- globalinfo-end -->
 * 
 * <!-- technical-bibtex-start --> BibTeX:
 * 
 * <pre>
 * &#64;article{Huehn2009,
 *    author = {Jens Christian Huehn and Eyke Huellermeier},
 *    journal = {Data Mining and Knowledge Discovery},
 *    title = {FURIA: An Algorithm for Unordered Fuzzy Rule Induction},
 *    year = {2009}
 * }
 * </pre>
 * <p/>
 * <!-- technical-bibtex-end -->
 * 
 * <!-- options-start --> Valid options are:
 * <p/>
 * 
 * <pre>
 * -F &lt;number of folds&gt;
 *  Set number of folds for REP
 *  One fold is used as pruning set.
 *  (default 3)
 * </pre>
 * 
 * <pre>
 * -N &lt;min. weights&gt;
 *  Set the minimal weights of instances
 *  within a split.
 *  (default 2.0)
 * </pre>
 * 
 * <pre>
 * -O &lt;number of runs&gt;
 *  Set the number of runs of
 *  optimizations. (Default: 2)
 * </pre>
 * 
 * <pre>
 * -D
 *  Set whether turn on the
 *  debug mode (Default: false)
 * </pre>
 * 
 * <pre>
 * -S &lt;seed&gt;
 *  The seed of randomization
 *  (Default: 1)
 * </pre>
 * 
 * <pre>
 * -E
 *  Whether NOT check the error rate&gt;=0.5
 *  in stopping criteria  (default: check)
 * </pre>
 * 
 * <pre>
 * -s
 *  The action performed for uncovered instances.
 *  (default: use stretching)
 * </pre>
 * 
 * <pre>
 * -p
 *  The T-norm used as fuzzy AND-operator.
 *  (default: Product T-norm)
 * </pre>
 * 
 * <!-- options-end -->
 * 
 * @author Jens Christian H&uuml;hn (huehn@gmx.net)
 * @author Xin Xu (xx5@cs.waikato.ac.nz)
 * @author Eibe Frank (eibe@cs.waikato.ac.nz)
 * @version $Revision$
 */
public class FURIA extends AbstractClassifier implements OptionHandler,
  AdditionalMeasureProducer, WeightedInstancesHandler,
  TechnicalInformationHandler {

  /** for serialization */
  static final long serialVersionUID = -6589312996832147161L;

  /** The limit of description length surplus in ruleset generation */
  private static double MAX_DL_SURPLUS = 64.0;

  /** The class attribute of the data */
  private Attribute m_Class;

  /** The ruleset */
  private ArrayList<Rule> m_Ruleset;

  /** The predicted class distribution */
  private ArrayList<double[]> m_Distributions;

  /** Runs of optimizations */
  private int m_Optimizations = 2;

  /** Random object used in this class */
  private Random m_Random = null;

  /** # of all the possible conditions in a rule */
  private double m_Total = 0;

  /** The seed to perform randomization */
  private long m_Seed = 1;

  /** The number of folds to split data into Grow and Prune for IREP */
  private int m_Folds = 3;

  /** The minimal number of instance weights within a split */
  private double m_MinNo = 2.0;

  /** Whether in a debug mode */
  private boolean m_Debug = false;

  /** Whether check the error rate >= 0.5 in stopping criteria */
  private boolean m_CheckErr = true;

  /** The class distribution of the training data */
  private double[] aprioriDistribution;

  /** The RuleStats for the ruleset of each class value */
  private ArrayList<RuleStats> m_RulesetStats;

  /** What to do if instance is uncovered */
  private int m_uncovAction = UNCOVACTION_STRETCH;

  /** An uncovered instance is covered using rule stretching. */
  private static final int UNCOVACTION_STRETCH = 0;

  /**
   * An uncovered instance is classified according to the training data class
   * distribution.
   */
  private static final int UNCOVACTION_APRIORI = 1;

  /** An uncovered instance is not classified at all. */
  private static final int UNCOVACTION_REJECT = 2;

  /** The tags explaining the uncovered action. */
  private static final Tag[] TAGS_UNCOVACTION = {
    new Tag(UNCOVACTION_STRETCH, "Apply rule stretching (standard)"),
    new Tag(UNCOVACTION_APRIORI, "Vote for the most frequent class"),
    new Tag(UNCOVACTION_REJECT, "Reject the decision and abstain") };

  /** Whether using product T-norm (or else min T-norm) */
  private int m_tNorm = TNORM_PROD;

  /** The Product T-Norm flag. */
  private static final int TNORM_PROD = 0;

  /** The Minimum T-Norm flag. */
  private static final int TNORM_MIN = 1;

  /** The tags describing the T-norms */
  private static final Tag[] TAGS_TNORM = {
    new Tag(TNORM_PROD, "Product T-Norm (standard)"),
    new Tag(TNORM_MIN, "Minimum T-Norm") };

  /**
   * Returns a string describing classifier
   * 
   * @return a description suitable for displaying in the explorer/experimenter
   *         gui
   */
  public String globalInfo() {

    return "FURIA: Fuzzy Unordered Rule Induction Algorithm\n\n"
      + "Details please see:\n\n" + getTechnicalInformation().toString()
      + "\n\n";
  }

  /**
   * Returns an instance of a TechnicalInformation object, containing detailed
   * information about the technical background of this class, e.g., paper
   * reference or book this class is based on.
   * 
   * @return the technical information about this class
   */
  @Override
  public TechnicalInformation getTechnicalInformation() {
    TechnicalInformation result;

    result = new TechnicalInformation(Type.ARTICLE);
    result.setValue(Field.AUTHOR, "Jens Christian Huehn and Eyke Huellermeier");
    result.setValue(Field.TITLE,
      "FURIA: An Algorithm for Unordered Fuzzy Rule Induction");
    result.setValue(Field.YEAR, "2009");
    result.setValue(Field.JOURNAL, "Data Mining and Knowledge Discovery");
    return result;
  }

  /**
   * Returns an enumeration describing the available options Valid options are:
   * <p>
   * 
   * -F number <br>
   * The number of folds for reduced error pruning. One fold is used as the
   * pruning set. (Default: 3)
   * <p>
   * 
   * -N number <br>
   * The minimal weights of instances within a split. (Default: 2)
   * <p>
   * 
   * -O number <br>
   * Set the number of runs of optimizations. (Default: 2)
   * <p>
   * 
   * -D <br>
   * Whether turn on the debug mode
   * 
   * -S number <br>
   * The seed of randomization used in FURIA.(Default: 1)
   * <p>
   * 
   * -E <br>
   * Whether NOT check the error rate >= 0.5 in stopping criteria. (default:
   * check)
   * <p>
   * 
   * -s <br>
   * The action performed for uncovered instances. (default: use rule
   * stretching)
   * <p>
   * 
   * -p <br>
   * The T-Norm used as fuzzy AND-operator. (default: Product T-Norm)
   * <p>
   * 
   * @return an enumeration of all the available options
   */
  @Override
  public Enumeration<Option> listOptions() {
    Vector<Option> newVector = new Vector<Option>(8);
    newVector.addElement(new Option("\tSet number of folds for REP\n"
      + "\tOne fold is used as pruning set.\n" + "\t(default 3)", "F", 1,
      "-F <number of folds>"));

    newVector
      .addElement(new Option("\tSet the minimal weights of instances\n"
        + "\twithin a split.\n" + "\t(default 2.0)", "N", 1,
        "-N <min. weights>"));

    newVector.addElement(new Option("\tSet the number of runs of\n"
      + "\toptimizations. (Default: 2)", "O", 1, "-O <number of runs>"));

    newVector.addElement(new Option("\tSet whether turn on the\n"
      + "\tdebug mode (Default: false)", "D", 0, "-D"));

    newVector.addElement(new Option("\tThe seed of randomization\n"
      + "\t(Default: 1)", "S", 1, "-S <seed>"));

    newVector.addElement(new Option("\tWhether NOT check the error rate>=0.5\n"
      + "\tin stopping criteria " + "\t(default: check)", "E", 0, "-E"));

    newVector.addElement(new Option(
      "\tThe action performed for uncovered instances.\n"
        + "\t(default: use stretching)", "s", 1, "-s"));

    newVector.addElement(new Option(
      "\tThe T-norm used as fuzzy AND-operator.\n"
        + "\t(default: Product T-norm)", "p", 1, "-p"));

    newVector.addAll(Collections.list(super.listOptions()));

    return newVector.elements();
  }

  /**
   * Parses a given list of options.
   * <p/>
   * 
   * <!-- options-start --> Valid options are:
   * <p/>
   * 
   * <pre>
   * -F &lt;number of folds&gt;
   *  Set number of folds for REP
   *  One fold is used as pruning set.
   *  (default 3)
   * </pre>
   * 
   * <pre>
   * -N &lt;min. weights&gt;
   *  Set the minimal weights of instances
   *  within a split.
   *  (default 2.0)
   * </pre>
   * 
   * <pre>
   * -O &lt;number of runs&gt;
   *  Set the number of runs of
   *  optimizations. (Default: 2)
   * </pre>
   * 
   * <pre>
   * -D
   *  Set whether turn on the
   *  debug mode (Default: false)
   * </pre>
   * 
   * <pre>
   * -S &lt;seed&gt;
   *  The seed of randomization
   *  (Default: 1)
   * </pre>
   * 
   * <pre>
   * -E
   *  Whether NOT check the error rate&gt;=0.5
   *  in stopping criteria  (default: check)
   * </pre>
   * 
   * <pre>
   * -s
   *  The action performed for uncovered instances.
   *  (default: use stretching)
   * </pre>
   * 
   * <pre>
   * -p
   *  The T-norm used as fuzzy AND-operator.
   *  (default: Product T-norm)
   * </pre>
   * 
   * <!-- options-end -->
   * 
   * @param options the list of options as an array of strings
   * @throws Exception if an option is not supported
   */
  @Override
  public void setOptions(String[] options) throws Exception {
    String numFoldsString = Utils.getOption('F', options);
    if (numFoldsString.length() != 0) {
      m_Folds = Integer.parseInt(numFoldsString);
    } else {
      m_Folds = 3;
    }

    String minNoString = Utils.getOption('N', options);
    if (minNoString.length() != 0) {
      m_MinNo = Double.parseDouble(minNoString);
    } else {
      m_MinNo = 2.0;
    }

    String seedString = Utils.getOption('S', options);
    if (seedString.length() != 0) {
      m_Seed = Long.parseLong(seedString);
    } else {
      m_Seed = 1;
    }

    String runString = Utils.getOption('O', options);
    if (runString.length() != 0) {
      m_Optimizations = Integer.parseInt(runString);
    } else {
      m_Optimizations = 2;
    }

    String tNormString = Utils.getOption('p', options);
    if (tNormString.length() != 0) {
      m_tNorm = Integer.parseInt(tNormString);
    } else {
      m_tNorm = TNORM_PROD;
    }

    String uncovActionString = Utils.getOption('s', options);
    if (uncovActionString.length() != 0) {
      m_uncovAction = Integer.parseInt(uncovActionString);
    } else {
      m_uncovAction = UNCOVACTION_STRETCH;
    }

    m_Debug = Utils.getFlag('D', options);

    m_CheckErr = !Utils.getFlag('E', options);

    super.setOptions(options);
  }

  /**
   * Gets the current settings of the Classifier.
   * 
   * @return an array of strings suitable for passing to setOptions
   */
  @Override
  public String[] getOptions() {

    Vector<String> options = new Vector<String>();

    options.add("-F");
    options.add("" + m_Folds);
    options.add("-N");
    options.add("" + m_MinNo);
    options.add("-O");
    options.add("" + m_Optimizations);
    options.add("-S");
    options.add("" + m_Seed);
    options.add("-p");
    options.add("" + m_tNorm);
    options.add("-s");
    options.add("" + m_uncovAction);

    if (m_Debug) {
      options.add("-D");
    }

    if (!m_CheckErr) {
      options.add("-E");
    }

    Collections.addAll(options, super.getOptions());

    return options.toArray(new String[0]);
  }

  /**
   * Returns an enumeration of the additional measure names
   * 
   * @return an enumeration of the measure names
   */
  @Override
  public Enumeration<String> enumerateMeasures() {
    Vector<String> newVector = new Vector<String>(1);
    newVector.addElement("measureNumRules");
    return newVector.elements();
  }

  /**
   * Returns the value of the named measure
   * 
   * @param additionalMeasureName the name of the measure to query for its value
   * @return the value of the named measure
   * @throws IllegalArgumentException if the named measure is not supported
   */
  @Override
  public double getMeasure(String additionalMeasureName) {
    if (additionalMeasureName.compareToIgnoreCase("measureNumRules") == 0) {
      return m_Ruleset.size();
    } else {
      throw new IllegalArgumentException(additionalMeasureName
        + " not supported (FURIA)");
    }
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String foldsTipText() {
    return "Determines the amount of data used for pruning. One fold is used for "
      + "pruning, the rest for growing the rules.";
  }

  /**
   * Sets the number of folds to use
   * 
   * @param fold the number of folds
   */
  public void setFolds(int fold) {
    m_Folds = fold;
  }

  /**
   * Gets the number of folds
   * 
   * @return the number of folds
   */
  public int getFolds() {
    return m_Folds;
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String minNoTipText() {
    return "The minimum total weight of the instances in a rule.";
  }

  /**
   * Sets the minimum total weight of the instances in a rule
   * 
   * @param m the minimum total weight of the instances in a rule
   */
  public void setMinNo(double m) {
    m_MinNo = m;
  }

  /**
   * Gets the minimum total weight of the instances in a rule
   * 
   * @return the minimum total weight of the instances in a rule
   */
  public double getMinNo() {
    return m_MinNo;
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String seedTipText() {
    return "The seed used for randomizing the data.";
  }

  /**
   * Sets the seed value to use in randomizing the data
   * 
   * @param s the new seed value
   */
  public void setSeed(long s) {
    m_Seed = s;
  }

  /**
   * Gets the current seed value to use in randomizing the data
   * 
   * @return the seed value
   */
  public long getSeed() {
    return m_Seed;
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String optimizationsTipText() {
    return "The number of optimization runs.";
  }

  /**
   * Sets the number of optimization runs
   * 
   * @param run the number of optimization runs
   */
  public void setOptimizations(int run) {
    m_Optimizations = run;
  }

  /**
   * Gets the the number of optimization runs
   * 
   * @return the number of optimization runs
   */
  public int getOptimizations() {
    return m_Optimizations;
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  @Override
  public String debugTipText() {
    return "Whether debug information is output to the console.";
  }

  /**
   * Sets whether debug information is output to the console
   * 
   * @param d whether debug information is output to the console
   */
  @Override
  public void setDebug(boolean d) {
    m_Debug = d;
  }

  /**
   * Gets whether debug information is output to the console
   * 
   * @return whether debug information is output to the console
   */
  @Override
  public boolean getDebug() {
    return m_Debug;
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String checkErrorRateTipText() {
    return "Whether check for error rate >= 1/2 is included"
      + " in stopping criterion.";
  }

  /**
   * Sets whether to check for error rate is in stopping criterion
   * 
   * @param d whether to check for error rate is in stopping criterion
   */
  public void setCheckErrorRate(boolean d) {
    m_CheckErr = d;
  }

  /**
   * Gets whether to check for error rate is in stopping criterion
   * 
   * @return true if checking for error rate is in stopping criterion
   */
  public boolean getCheckErrorRate() {
    return m_CheckErr;
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String uncovActionTipText() {
    return "Selet the action that is performed for uncovered instances.";
  }

  /**
   * Gets the action that is performed for uncovered instances. It can be
   * UNCOVACTION_STRETCH, UNCOVACTION_APRIORI or UNCOVACTION_REJECT.
   * 
   * @return the current TNorm.
   */
  public SelectedTag getUncovAction() {
    return new SelectedTag(m_uncovAction, TAGS_UNCOVACTION);
  }

  /**
   * Sets the action that is performed for uncovered instances. It can be
   * UNCOVACTION_STRETCH, UNCOVACTION_APRIORI or UNCOVACTION_REJECT.
   * 
   * @param newUncovAction the new action.
   */
  public void setUncovAction(SelectedTag newUncovAction) {
    if (newUncovAction.getTags() == TAGS_UNCOVACTION) {
      m_uncovAction = newUncovAction.getSelectedTag().getID();
    }
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String TNormTipText() {
    return "Choose the T-Norm that is used as fuzzy AND-operator.";
  }

  /**
   * Gets the TNorm used. Will be either TNORM_PROD or TNORM_MIN.
   * 
   * @return the current TNorm.
   */
  public SelectedTag getTNorm() {
    return new SelectedTag(m_tNorm, TAGS_TNORM);
  }

  /**
   * Sets the TNorm used. Will be either TNORM_PROD or TNORM_MIN.
   * 
   * @param newTNorm the new TNorm.
   */
  public void setTNorm(SelectedTag newTNorm) {
    if (newTNorm.getTags() == TAGS_TNORM) {
      m_tNorm = newTNorm.getSelectedTag().getID();
    }
  }

  /**
   * Get the ruleset generated by FURIA
   * 
   * @return the ruleset
   */
  public ArrayList<Rule> getRuleset() {
    return m_Ruleset;
  }

  /**
   * Get the statistics of the ruleset in the given position
   * 
   * @param pos the position of the stats, assuming correct
   * @return the statistics of the ruleset in the given position
   */
  public RuleStats getRuleStats(int pos) {
    return m_RulesetStats.get(pos);
  }

  /**
   * The single antecedent in the rule, which is composed of an attribute and
   * the corresponding value. There are two inherited classes, namely
   * NumericAntd and NominalAntd in which the attributes are numeric and nominal
   * respectively.
   */
  @SuppressWarnings("serial")
  protected abstract class Antd implements WeightedInstancesHandler, Copyable,
    Serializable {

    /** The attribute of the antecedent */
    public Attribute att;

    /**
     * The attribute value of the antecedent. For numeric attribute, value is
     * either 0(1st bag) or 1(2nd bag)
     */
    public double value;

    /**
     * The maximum infoGain achieved by this antecedent test in the growing data
     */
    protected double maxInfoGain;

    /** The accurate rate of this antecedent test on the growing data */
    protected double accuRate;

    /** The coverage of this antecedent in the growing data */
    protected double cover;

    /** The accurate data for this antecedent in the growing data */
    protected double accu;

    /**
     * Confidence / weight of this rule for the rule stretching procedure that
     * is returned when this is the last antecedent of the rule.
     */
    double weightOfTheRuleWhenItIsPrunedAfterThisAntecedent = 0;

    /** Confidence / weight of this antecedent. */
    public double m_confidence = 0.0;

    /**
     * Constructor
     */
    public Antd(Attribute a) {
      att = a;
      value = Double.NaN;
      maxInfoGain = 0;
      accuRate = Double.NaN;
      cover = Double.NaN;
      accu = Double.NaN;
    }

    /* The abstract members for inheritance */
    public abstract Instances[] splitData(Instances data, double defAcRt,
      double cla);

    public abstract double covers(Instance inst);

    @Override
    public abstract String toString();

    /**
     * Implements Copyable
     * 
     * @return a copy of this object
     */
    @Override
    public abstract Object copy();

    /* Get functions of this antecedent */
    public Attribute getAttr() {
      return att;
    }

    public double getAttrValue() {
      return value;
    }

    public double getMaxInfoGain() {
      return maxInfoGain;
    }

    public double getAccuRate() {
      return accuRate;
    }

    public double getAccu() {
      return accu;
    }

    public double getCover() {
      return cover;
    }
  }

  /**
   * The antecedent with numeric attribute
   */
  public class NumericAntd extends Antd {

    /** for serialization */
    static final long serialVersionUID = 5699457269983735442L;

    /** The split point for this numeric antecedent */
    public double splitPoint;

    /** The edge point for the fuzzy set of this numeric antecedent */
    public double supportBound;

    /**
     * A flag determining whether this antecedent was successfully fuzzified yet
     */
    public boolean fuzzyYet = false;

    /**
     * Constructor
     */
    public NumericAntd(Attribute a) {
      super(a);
      splitPoint = Double.NaN;
      supportBound = Double.NaN;
    }

    /**
     * Get split point of this numeric antecedent
     * 
     * @return the split point of this numeric antecedent
     */
    public double getSplitPoint() {
      return splitPoint;
    }

    /**
     * Implements Copyable
     * 
     * @return a copy of this object
     */
    @Override
    public Object copy() {
      NumericAntd na = new NumericAntd(getAttr());
      na.m_confidence = m_confidence;
      na.value = this.value;
      na.splitPoint = this.splitPoint;
      na.supportBound = this.supportBound;
      na.fuzzyYet = this.fuzzyYet;
      return na;
    }

    /**
     * Implements the splitData function. This procedure is to split the data
     * into two bags according to the information gain of the numeric attribute
     * value The maximum infoGain is also calculated.
     * 
     * @param insts the data to be split
     * @param defAcRt the default accuracy rate for data
     * @param cl the class label to be predicted
     * @return the array of data after split
     */
    @Override
    public Instances[] splitData(Instances insts, double defAcRt, double cl) {
      Instances data = insts;
      int total = data.numInstances();// Total number of instances without
      // missing value for att

      int split = 1; // Current split position
      int prev = 0; // Previous split position
      int finalSplit = split; // Final split position
      maxInfoGain = 0;
      value = 0;

      double fstCover = 0, sndCover = 0, fstAccu = 0, sndAccu = 0;

      data.sort(att);
      // Find the las instance without missing value
      for (int x = 0; x < data.numInstances(); x++) {
        Instance inst = data.instance(x);
        if (inst.isMissing(att)) {
          total = x;
          break;
        }

        sndCover += inst.weight();
        if (Utils.eq(inst.classValue(), cl)) {
          sndAccu += inst.weight();
        }
      }

      if (total == 0) {
        return null; // Data all missing for the attribute
      }
      splitPoint = data.instance(total - 1).value(att);

      for (; split <= total; split++) {
        if ((split == total) || (data.instance(split).value(att) > // Can't
                                                                   // split
                                                                   // within
          data.instance(prev).value(att))) { // same value

          for (int y = prev; y < split; y++) {
            Instance inst = data.instance(y);
            fstCover += inst.weight();
            if (Utils.eq(data.instance(y).classValue(), cl)) {
              fstAccu += inst.weight(); // First bag positive# ++
            }
          }

          double fstAccuRate = (fstAccu + 1.0) / (fstCover + 1.0), sndAccuRate = (sndAccu + 1.0)
            / (sndCover + 1.0);

          /* Which bag has higher information gain? */
          boolean isFirst;
          double fstInfoGain, sndInfoGain;
          double accRate, infoGain, coverage, accurate;

          fstInfoGain =
          // Utils.eq(defAcRt, 1.0) ?
          // fstAccu/(double)numConds :
          fstAccu * (Utils.log2(fstAccuRate) - Utils.log2(defAcRt));

          sndInfoGain =
          // Utils.eq(defAcRt, 1.0) ?
          // sndAccu/(double)numConds :
          sndAccu * (Utils.log2(sndAccuRate) - Utils.log2(defAcRt));

          if (fstInfoGain > sndInfoGain) {
            isFirst = true;
            infoGain = fstInfoGain;
            accRate = fstAccuRate;
            accurate = fstAccu;
            coverage = fstCover;
          } else {
            isFirst = false;
            infoGain = sndInfoGain;
            accRate = sndAccuRate;
            accurate = sndAccu;
            coverage = sndCover;
          }

          /* Check whether so far the max infoGain */
          if (infoGain > maxInfoGain) {
            splitPoint = data.instance(prev).value(att);
            value = (isFirst) ? 0 : 1;
            accuRate = accRate;
            accu = accurate;
            cover = coverage;
            maxInfoGain = infoGain;
            finalSplit = (isFirst) ? split : prev;
          }

          for (int y = prev; y < split; y++) {
            Instance inst = data.instance(y);
            sndCover -= inst.weight();
            if (Utils.eq(data.instance(y).classValue(), cl)) {
              sndAccu -= inst.weight(); // Second bag positive# --
            }
          }
          prev = split;
        }
      }

      /* Split the data */
      Instances[] splitData = new Instances[2];
      splitData[0] = new Instances(data, 0, finalSplit);
      splitData[1] = new Instances(data, finalSplit, total - finalSplit);

      return splitData;
    }

    /**
     * The degree of coverage for the instance given that antecedent
     * 
     * @param inst the instance in question
     * @return the numeric value indicating the membership of the instance for
     *         this antecedent
     */
    @Override
    public double covers(Instance inst) {
      double isCover = 0;
      if (!inst.isMissing(att)) {
        if ((int) value == 0) { // First bag
          if (inst.value(att) <= splitPoint) {
            isCover = 1;
          } else if (fuzzyYet && (inst.value(att) > splitPoint)
            && (inst.value(att) < supportBound)) {
            isCover = 1 - ((inst.value(att) - splitPoint) / (supportBound - splitPoint));
          }
        } else {
          if (inst.value(att) >= splitPoint) {
            isCover = 1;
          } else if (fuzzyYet && inst.value(att) < splitPoint
            && (inst.value(att) > supportBound)) {
            isCover = 1 - ((splitPoint - inst.value(att)) / (splitPoint - supportBound));
          }
        }
      }

      return isCover;
    }

    /**
     * Prints this antecedent
     * 
     * @return a textual description of this antecedent
     */
    @Override
    public String toString() {
      if (value == 0) {
        if (fuzzyYet) {
          return (att.name() + " in [-inf, -inf, "
            + Utils.doubleToString(splitPoint, 6) + ", "
            + Utils.doubleToString(supportBound, 6) + "]");
        }
        return (att.name() + " in [-inf, "
          + Utils.doubleToString(splitPoint, 6) + "]");
      } else {
        if (fuzzyYet) {
          return (att.name() + " in [" + Utils.doubleToString(supportBound, 6)
            + ", " + Utils.doubleToString(splitPoint, 6) + ", inf, inf]");
        }
        return (att.name() + " in [" + Utils.doubleToString(splitPoint, 6) + ", inf]");
      }

    }

  }

  /**
   * The antecedent with nominal attribute
   */
  protected class NominalAntd extends Antd {

    /** for serialization */
    static final long serialVersionUID = -9102297038837585135L;

    /*
     * The parameters of infoGain calculated for each attribute value in the
     * growing data
     */
    private final double[] accurate;
    private final double[] coverage;

    /**
     * Constructor
     */
    public NominalAntd(Attribute a) {
      super(a);
      int bag = att.numValues();
      accurate = new double[bag];
      coverage = new double[bag];
    }

    /**
     * Implements Copyable
     * 
     * @return a copy of this object
     */
    @Override
    public Object copy() {
      Antd antec = new NominalAntd(getAttr());
      antec.m_confidence = m_confidence;
      antec.value = this.value;
      return antec;
    }

    /**
     * Implements the splitData function. This procedure is to split the data
     * into bags according to the nominal attribute value The infoGain for each
     * bag is also calculated.
     * 
     * @param data the data to be split
     * @param defAcRt the default accuracy rate for data
     * @param cl the class label to be predicted
     * @return the array of data after split
     */
    @Override
    public Instances[] splitData(Instances data, double defAcRt, double cl) {
      int bag = att.numValues();
      Instances[] splitData = new Instances[bag];

      for (int x = 0; x < bag; x++) {
        splitData[x] = new Instances(data, data.numInstances());
        accurate[x] = 0;
        coverage[x] = 0;
      }

      for (int x = 0; x < data.numInstances(); x++) {
        Instance inst = data.instance(x);
        if (!inst.isMissing(att)) {
          int v = (int) inst.value(att);
          splitData[v].add(inst);
          coverage[v] += inst.weight();
          if ((int) inst.classValue() == (int) cl) {
            accurate[v] += inst.weight();
          }
        }
      }

      for (int x = 0; x < bag; x++) {
        double t = coverage[x] + 1.0;
        double p = accurate[x] + 1.0;
        double infoGain =
        // Utils.eq(defAcRt, 1.0) ?
        // accurate[x]/(double)numConds :
        accurate[x] * (Utils.log2(p / t) - Utils.log2(defAcRt));

        if (infoGain > maxInfoGain) {
          maxInfoGain = infoGain;
          cover = coverage[x];
          accu = accurate[x];
          accuRate = p / t;
          value = x;
        }
      }

      return splitData;
    }

    /**
     * Whether the instance is covered by this antecedent
     * 
     * @param inst the instance in question
     * @return the boolean value indicating whether the instance is covered by
     *         this antecedent
     */
    @Override
    public double covers(Instance inst) {
      double isCover = 0;
      if (!inst.isMissing(att)) {
        if ((int) inst.value(att) == (int) value) {
          isCover = 1;
        }
      }
      return isCover;
    }

    /**
     * Prints this antecedent
     * 
     * @return a textual description of this antecedent
     */
    @Override
    public String toString() {
      return (att.name() + " = " + att.value((int) value));
    }
  }

  /**
   * This class implements a single rule that predicts specified class.
   * 
   * A rule consists of antecedents "AND"ed together and the consequent (class
   * value) for the classification. In this class, the Information Gain
   * (p*[log(p/t) - log(P/T)]) is used to select an antecedent and Reduced Error
   * Prunning (REP) with the metric of accuracy rate p/(p+n) or (TP+TN)/(P+N) is
   * used to prune the rule.
   */
  public class RipperRule extends Rule {

    /** for serialization */
    static final long serialVersionUID = -2410020717305262952L;

    /** The internal representation of the class label to be predicted */
    double m_Consequent = -1;

    /** The vector of antecedents of this rule */
    public ArrayList<Antd> m_Antds = null;

    /** Constructor */
    public RipperRule() {
      m_Antds = new ArrayList<Antd>();
    }

    /**
     * Sets the internal representation of the class label to be predicted
     * 
     * @param cl the internal representation of the class label to be predicted
     */
    public void setConsequent(double cl) {
      m_Consequent = cl;
    }

    /**
     * Gets the internal representation of the class label to be predicted
     * 
     * @return the internal representation of the class label to be predicted
     */
    @Override
    public double getConsequent() {
      return m_Consequent;
    }

    /**
     * Get a shallow copy of this rule
     * 
     * @return the copy
     */
    @Override
    public Object copy() {
      RipperRule copy = new RipperRule();
      copy.setConsequent(getConsequent());
      copy.m_Antds = new ArrayList<Antd>();
      for (Antd a : this.m_Antds) {
        copy.m_Antds.add((Antd) a.copy());
      }
      return copy;
    }

    /**
     * The degree of coverage instance covered by this rule
     * 
     * @param datum the instance in question
     * @return the degree to which the instance is covered by this rule
     */
    public double coverageDegree(Instance datum) {
      double coverage = 1;

      for (int i = 0; i < m_Antds.size(); i++) {
        Antd antd = m_Antds.get(i);
        if (m_tNorm == TNORM_PROD) {
          // Product T-Norm
          if (antd instanceof NumericAntd) {
            coverage *= ((NumericAntd) antd).covers(datum);
          } else {
            coverage *= antd.covers(datum);
          }
        } else {
          // Min T-Norm
          if (antd instanceof NumericAntd) {
            coverage = Math.min(coverage, ((NumericAntd) antd).covers(datum));
          } else {
            coverage = Math.min(coverage, antd.covers(datum));
          }
        }

      }

      return coverage;
    }

    /**
     * Whether the instance covered by this rule
     * 
     * @param datum the instance in question
     * @return the boolean value indicating whether the instance is covered by
     *         this rule
     */
    @Override
    public boolean covers(Instance datum) {
      if (coverageDegree(datum) == 0) {
        return false;
      } else {
        return true;
      }
    }

    /**
     * Whether this rule has antecedents, i.e. whether it is a default rule
     * 
     * @return the boolean value indicating whether the rule has antecedents
     */
    @Override
    public boolean hasAntds() {
      if (m_Antds == null) {
        return false;
      } else {
        return (m_Antds.size() > 0);
      }
    }

    /**
     * the number of antecedents of the rule
     * 
     * @return the size of this rule
     */
    @Override
    public double size() {
      return m_Antds.size();
    }

    /**
     * Private function to compute default number of accurate instances in the
     * specified data for the consequent of the rule
     * 
     * @param data the data in question
     * @return the default accuracy number
     */
    private double computeDefAccu(Instances data) {
      double defAccu = 0;
      for (int i = 0; i < data.numInstances(); i++) {
        Instance inst = data.instance(i);
        if ((int) inst.classValue() == (int) m_Consequent) {
          defAccu += inst.weight();
        }
      }
      return defAccu;
    }

    /**
     * Build one rule using the growing data
     * 
     * @param data the growing data used to build the rule
     * @throws Exception if the consequent is not set yet
     */
    @Override
    public void grow(Instances data) throws Exception {
      if (m_Consequent == -1) {
        throw new Exception(" Consequent not set yet.");
      }

      Instances growData = data;
      double sumOfWeights = growData.sumOfWeights();
      if (!Utils.gr(sumOfWeights, 0.0)) {
        return;
      }

      /* Compute the default accurate rate of the growing data */
      double defAccu = computeDefAccu(growData);
      double defAcRt = (defAccu + 1.0) / (sumOfWeights + 1.0);

      /* Keep the record of which attributes have already been used */
      boolean[] used = new boolean[growData.numAttributes()];
      for (int k = 0; k < used.length; k++) {
        used[k] = false;
      }
      int numUnused = used.length;

      // If there are already antecedents existing
      for (int j = 0; j < m_Antds.size(); j++) {
        Antd antdj = m_Antds.get(j);
        if (!antdj.getAttr().isNumeric()) {
          used[antdj.getAttr().index()] = true;
          numUnused--;
        }
      }

      double maxInfoGain;
      while (Utils.gr(growData.numInstances(), 0.0) && (numUnused > 0)
        && Utils.sm(defAcRt, 1.0)) {

        // We require that infoGain be positive
        /*
         * if(numAntds == originalSize) maxInfoGain = 0.0; // At least one
         * condition allowed else maxInfoGain = Utils.eq(defAcRt, 1.0) ?
         * defAccu/(double)numAntds : 0.0;
         */
        maxInfoGain = 0.0;

        /* Build a list of antecedents */
        Antd oneAntd = null;
        Instances coverData = null;
        Enumeration<Attribute> enumAttr = growData.enumerateAttributes();

        /* Build one condition based on all attributes not used yet */
        while (enumAttr.hasMoreElements()) {
          Attribute att = (enumAttr.nextElement());

          if (m_Debug) {
            System.err.println("\nOne condition: size = "
              + growData.sumOfWeights());
          }

          Antd antd = null;
          if (att.isNumeric()) {
            antd = new NumericAntd(att);
          } else {
            antd = new NominalAntd(att);
          }

          if (!used[att.index()]) {
            /*
             * Compute the best information gain for each attribute, it's stored
             * in the antecedent formed by this attribute. This procedure
             * returns the data covered by the antecedent
             */
            Instances coveredData = computeInfoGain(growData, defAcRt, antd);
            if (coveredData != null) {
              double infoGain = antd.getMaxInfoGain();
              if (m_Debug) {
                System.err.println("Test of \'" + antd.toString()
                  + "\': infoGain = " + infoGain + " | Accuracy = "
                  + antd.getAccuRate() + "=" + antd.getAccu() + "/"
                  + antd.getCover() + " def. accuracy: " + defAcRt);
              }

              if (infoGain > maxInfoGain) {
                oneAntd = antd;
                coverData = coveredData;
                maxInfoGain = infoGain;
              }
            }
          }
        }

        if (oneAntd == null) {
          break; // Cannot find antds
        }
        if (Utils.sm(oneAntd.getAccu(), m_MinNo)) {
          break;// Too low coverage
        }

        // Numeric attributes can be used more than once
        if (!oneAntd.getAttr().isNumeric()) {
          used[oneAntd.getAttr().index()] = true;
          numUnused--;
        }

        m_Antds.add(oneAntd);

        growData = coverData;// Grow data size is shrinking
        defAcRt = oneAntd.getAccuRate();
      }
    }

    /**
     * Compute the best information gain for the specified antecedent
     * 
     * @param instances the data based on which the infoGain is computed
     * @param defAcRt the default accuracy rate of data
     * @param antd the specific antecedent
     * @return the data covered by the antecedent
     */
    private Instances computeInfoGain(Instances instances, double defAcRt,
      Antd antd) {
      Instances data = instances;

      /*
       * Split the data into bags. The information gain of each bag is also
       * calculated in this procedure
       */
      Instances[] splitData = antd.splitData(data, defAcRt, m_Consequent);

      /* Get the bag of data to be used for next antecedents */
      if (splitData != null) {
        return splitData[(int) antd.getAttrValue()];
      } else {
        return null;
      }
    }

    /**
     * Prune all the possible final sequences of the rule using the pruning
     * data. The measure used to prune the rule is based on flag given.
     * 
     * @param pruneData the pruning data used to prune the rule
     * @param useWhole flag to indicate whether use the error rate of the whole
     *          pruning data instead of the data covered
     */
    public void prune(Instances pruneData, boolean useWhole) {
      Instances data = pruneData;

      double total = data.sumOfWeights();
      if (!Utils.gr(total, 0.0)) {
        return;
      }

      /* The default accurate # and rate on pruning data */
      double defAccu = computeDefAccu(data);

      if (m_Debug) {
        System.err.println("Pruning with " + defAccu + " positive data out of "
          + total + " instances");
      }

      int size = m_Antds.size();
      if (size == 0) {
        return; // Default rule before pruning
      }

      double[] worthRt = new double[size];
      double[] coverage = new double[size];
      double[] worthValue = new double[size];
      for (int w = 0; w < size; w++) {
        worthRt[w] = coverage[w] = worthValue[w] = 0.0;
      }

      /* Calculate accuracy parameters for all the antecedents in this rule */
      double tn = 0.0; // True negative if useWhole
      for (int x = 0; x < size; x++) {
        Antd antd = m_Antds.get(x);
        Instances newData = data;
        data = new Instances(newData, 0); // Make data empty

        for (int y = 0; y < newData.numInstances(); y++) {
          Instance ins = newData.instance(y);

          if (antd.covers(ins) > 0) { // Covered by this antecedent
            coverage[x] += ins.weight();
            data.add(ins); // Add to data for further pruning
            if ((int) ins.classValue() == (int) m_Consequent) {
              worthValue[x] += ins.weight();
            }
          } else if (useWhole) { // Not covered
            if ((int) ins.classValue() != (int) m_Consequent) {
              tn += ins.weight();
            }
          }
        }

        if (useWhole) {
          worthValue[x] += tn;
          worthRt[x] = worthValue[x] / total;
        } else {
          worthRt[x] = (worthValue[x] + 1.0) / (coverage[x] + 2.0);
        }
      }

      double maxValue = (defAccu + 1.0) / (total + 2.0);
      int maxIndex = -1;
      for (int i = 0; i < worthValue.length; i++) {
        if (m_Debug) {
          double denom = useWhole ? total : coverage[i];
          System.err.println(i + "(useAccuray? " + !useWhole + "): "
            + worthRt[i] + "=" + worthValue[i] + "/" + denom);
        }
        if (worthRt[i] > maxValue) { // Prefer to the
          maxValue = worthRt[i]; // shorter rule
          maxIndex = i;
        }
      }

      if (maxIndex == -1) {
        return;
      }

      /* Prune the antecedents according to the accuracy parameters */
      for (int z = size - 1; z > maxIndex; z--) {
        m_Antds.remove(z);
      }
    }

    /**
     * Prints this rule
     * 
     * @param classAttr the class attribute in the data
     * @return a textual description of this rule
     */
    public String toString(Attribute classAttr) {
      StringBuffer text = new StringBuffer();
      if (m_Antds.size() > 0) {
        for (int j = 0; j < (m_Antds.size() - 1); j++) {
          text.append("(" + (m_Antds.get(j)).toString() + ") and ");
        }
        text.append("(" + (m_Antds.get(m_Antds.size() - 1)).toString() + ")");
      }
      text.append(" => " + classAttr.name() + "="
        + classAttr.value((int) m_Consequent));

      return text.toString();
    }

    /**
     * The fuzzification procedure
     * 
     * @param data training data
     * @param allWeightsAreOne flag whether all instances have weight 1. If this
     *          is the case branch-and-bound is possible for speed-up.
     */
    public void fuzzify(Instances data, boolean allWeightsAreOne) {
      // Determine whether there are numeric antecedents that can be fuzzified.
      if (m_Antds == null) {
        return;
      }
      int numNumericAntds = 0;
      for (int i = 0; i < m_Antds.size(); i++) {
        if (m_Antds.get(i) instanceof NumericAntd) {
          numNumericAntds++;
        }
      }
      if (numNumericAntds == 0) {
        return;
      }

      double maxPurity = Double.NEGATIVE_INFINITY;
      boolean[] finishedAntecedents = new boolean[m_Antds.size()];
      int numFinishedAntecedents = 0;

      // Loop until all antecdents have been fuzzified
      while (numFinishedAntecedents < m_Antds.size()) {
        double maxPurityOfAllAntecedents = Double.NEGATIVE_INFINITY;
        int bestAntecedentsIndex = -1;
        double bestSupportBoundForAllAntecedents = Double.NaN;

        Instances relevantData = new Instances(data, 0);
        for (int j = 0; j < m_Antds.size(); j++) {
          if (finishedAntecedents[j]) {
            continue;
          }

          relevantData = new Instances(data);
          /*
           * Remove instances which are not relevant, because they are not
           * covered by the _other_ antecedents.
           */
          for (int k = 0; k < m_Antds.size(); k++) {
            if (k == j) {
              continue;
            }
            Antd exclusionAntd = (m_Antds.get(k));
            for (int y = 0; y < relevantData.numInstances(); y++) {
              if (exclusionAntd.covers(relevantData.instance(y)) == 0) {
                relevantData.delete(y--);
              }
            }
          }

          // test whether this antecedent is numeric and whether there is data
          // for making it fuzzy
          if (relevantData.attribute(m_Antds.get(j).att.index()).isNumeric()
            && relevantData.numInstances() > 0) {
            // Get a working copy of this antecedent
            NumericAntd currentAntd = (NumericAntd) ((NumericAntd) m_Antds
              .get(j)).copy();
            currentAntd.fuzzyYet = true;

            relevantData.deleteWithMissing(currentAntd.att.index());

            double sumOfWeights = relevantData.sumOfWeights();
            if (!Utils.gr(sumOfWeights, 0.0)) {
              return;
            }

            relevantData.sort(currentAntd.att.index());

            double maxPurityForThisAntecedent = 0;
            double bestFoundSupportBound = Double.NaN;

            double lastAccu = 0;
            double lastCover = 0;
            // Test all possible edge points
            if (currentAntd.value == 0) {
              for (int k = 1; k < relevantData.numInstances(); k++) {
                // break the loop if there is no gain (only works when all
                // instances have weight 1)
                if ((lastAccu + (relevantData.numInstances() - k - 1))
                  / (lastCover + (relevantData.numInstances() - k - 1)) < maxPurityForThisAntecedent
                  && allWeightsAreOne) {
                  break;
                }

                // Bag 1
                if (currentAntd.splitPoint < relevantData.instance(k).value(
                  currentAntd.att.index())
                  && relevantData.instance(k).value(currentAntd.att.index()) != relevantData
                    .instance(k - 1).value(currentAntd.att.index())) {
                  currentAntd.supportBound = relevantData.instance(k).value(
                    currentAntd.att.index());

                  // Calculate the purity of this fuzzification
                  double[] accuArray = new double[relevantData.numInstances()];
                  double[] coverArray = new double[relevantData.numInstances()];
                  for (int i = 0; i < relevantData.numInstances(); i++) {
                    coverArray[i] = relevantData.instance(i).weight();
                    double coverValue = currentAntd.covers(relevantData
                      .instance(i));
                    if (coverArray[i] >= coverValue
                      * relevantData.instance(i).weight()) {
                      coverArray[i] = coverValue
                        * relevantData.instance(i).weight();
                      if (relevantData.instance(i).classValue() == m_Consequent) {
                        accuArray[i] = coverValue
                          * relevantData.instance(i).weight();
                      }
                    }
                  }

                  // Test whether this fuzzification is the best one for this
                  // antecedent.
                  // Keep it if this is the case.
                  double purity = (Utils.sum(accuArray))
                    / (Utils.sum(coverArray));
                  if (purity >= maxPurityForThisAntecedent) {
                    maxPurityForThisAntecedent = purity;
                    bestFoundSupportBound = currentAntd.supportBound;
                  }
                  lastAccu = Utils.sum(accuArray);
                  lastCover = Utils.sum(coverArray);
                }
              }
            } else {
              for (int k = relevantData.numInstances() - 2; k >= 0; k--) {
                // break the loop if there is no gain (only works when all
                // instances have weight 1)
                if ((lastAccu + (k)) / (lastCover + (k)) < maxPurityForThisAntecedent
                  && allWeightsAreOne) {
                  break;
                }

                // Bag 2
                if (currentAntd.splitPoint > relevantData.instance(k).value(
                  currentAntd.att.index())
                  && relevantData.instance(k).value(currentAntd.att.index()) != relevantData
                    .instance(k + 1).value(currentAntd.att.index())) {
                  currentAntd.supportBound = relevantData.instance(k).value(
                    currentAntd.att.index());

                  // Calculate the purity of this fuzzification
                  double[] accuArray = new double[relevantData.numInstances()];
                  double[] coverArray = new double[relevantData.numInstances()];
                  for (int i = 0; i < relevantData.numInstances(); i++) {
                    coverArray[i] = relevantData.instance(i).weight();
                    double coverValue = currentAntd.covers(relevantData
                      .instance(i));
                    if (coverArray[i] >= coverValue
                      * relevantData.instance(i).weight()) {
                      coverArray[i] = coverValue
                        * relevantData.instance(i).weight();
                      if (relevantData.instance(i).classValue() == m_Consequent) {
                        accuArray[i] = coverValue
                          * relevantData.instance(i).weight();
                      }
                    }
                  }

                  // Test whether this fuzzification is the best one for this
                  // antecedent.
                  // Keep it if this is the case.
                  double purity = (Utils.sum(accuArray))
                    / (Utils.sum(coverArray));
                  if (purity >= maxPurityForThisAntecedent) {
                    maxPurityForThisAntecedent = purity;
                    bestFoundSupportBound = currentAntd.supportBound;
                  }
                  lastAccu = Utils.sum(accuArray);
                  lastCover = Utils.sum(coverArray);
                }
              }

            }

            // Test whether the best fuzzification for this antecedent is the
            // best one of all
            // antecedents considered so far.
            // Keep it if this is the case.
            if (maxPurityForThisAntecedent > maxPurityOfAllAntecedents) {
              bestAntecedentsIndex = j;
              bestSupportBoundForAllAntecedents = bestFoundSupportBound;
              maxPurityOfAllAntecedents = maxPurityForThisAntecedent;
            }
          } else {
            // Deal with a nominal antecedent.
            // Since there is no fuzzification it is already finished.
            finishedAntecedents[j] = true;
            numFinishedAntecedents++;
            continue;
          }
        }

        // Make the fuzzification step for the current antecedent real.
        if (maxPurity <= maxPurityOfAllAntecedents) {
          if (Double.isNaN(bestSupportBoundForAllAntecedents)) {
            ((NumericAntd) m_Antds.get(bestAntecedentsIndex)).supportBound = ((NumericAntd) m_Antds
              .get(bestAntecedentsIndex)).splitPoint;
          } else {
            ((NumericAntd) m_Antds.get(bestAntecedentsIndex)).supportBound = bestSupportBoundForAllAntecedents;
            ((NumericAntd) m_Antds.get(bestAntecedentsIndex)).fuzzyYet = true;
          }
          maxPurity = maxPurityOfAllAntecedents;
        }
        finishedAntecedents[bestAntecedentsIndex] = true;
        numFinishedAntecedents++;
      }

    }

    /**
     * Calculation of the rule weights / confidences for all beginning rule
     * stumps.
     * 
     * @param data The training data
     */
    public void calculateConfidences(Instances data) {
      RipperRule tempRule = (RipperRule) this.copy();

      while (tempRule.hasAntds()) {
        double acc = 0;
        double cov = 0;
        for (int i = 0; i < data.numInstances(); i++) {
          double membershipValue = tempRule.coverageDegree(data.instance(i))
            * data.instance(i).weight();
          cov += membershipValue;
          if (m_Consequent == data.instance(i).classValue()) {
            acc += membershipValue;
          }
        }

        // m-estimate
        double m = 2.0;
        this.m_Antds.get((int) tempRule.size() - 1).m_confidence = (acc + m
          * (aprioriDistribution[(int) m_Consequent] / Utils
            .sum(aprioriDistribution)))
          / (cov + m);
        tempRule.m_Antds.remove(tempRule.m_Antds.size() - 1);
      }
    }

    /**
     * Get the rule confidence.
     * 
     * @return rule confidence / weight
     */
    public double getConfidence() {
      if (!hasAntds()) {
        return Double.NaN;
      }
      return m_Antds.get(m_Antds.size() - 1).m_confidence;
    }

    /**
     * 
     */
    @Override
    public String getRevision() {
      return "1.0";
    }

  }

  /**
   * Returns default capabilities of the classifier.
   * 
   * @return the capabilities of this classifier
   */
  @Override
  public Capabilities getCapabilities() {
    Capabilities result = super.getCapabilities();
    result.disableAll();

    // attributes
    result.enable(Capability.NOMINAL_ATTRIBUTES);
    result.enable(Capability.NUMERIC_ATTRIBUTES);
    result.enable(Capability.DATE_ATTRIBUTES);
    result.enable(Capability.MISSING_VALUES);

    // class
    result.enable(Capability.NOMINAL_CLASS);
    result.enable(Capability.MISSING_CLASS_VALUES);

    // instances
    result.setMinimumNumberInstances(m_Folds);

    return result;
  }

  /**
   * Builds the FURIA rule-based model
   * 
   * @param instances the training data
   * @throws Exception if classifier can't be built successfully
   */
  @Override
  public void buildClassifier(Instances instances) throws Exception {
    // can classifier handle the data?
    getCapabilities().testWithFail(instances);

    // remove instances with missing class
    instances = new Instances(instances);
    instances.deleteWithMissingClass();

    // Learn the apriori distribution for later
    aprioriDistribution = new double[instances.classAttribute().numValues()];
    boolean allWeightsAreOne = true;
    for (int i = 0; i < instances.numInstances(); i++) {
      aprioriDistribution[(int) instances.instance(i).classValue()] += instances
        .instance(i).weight();
      if (allWeightsAreOne && instances.instance(i).weight() != 1.0) {
        allWeightsAreOne = false;
        break;
      }
    }

    m_Random = instances.getRandomNumberGenerator(m_Seed);
    m_Total = RuleStats.numAllConditions(instances);
    if (m_Debug) {
      System.err.println("Number of all possible conditions = " + m_Total);
    }

    Instances data = new Instances(instances);

    m_Class = data.classAttribute();
    m_Ruleset = new ArrayList<Rule>();
    m_RulesetStats = new ArrayList<RuleStats>();
    m_Distributions = new ArrayList<double[]>();

    // Learn a rule set for each single class
    oneClass: for (int y = 0; y < data.numClasses(); y++) { // For each class

      double classIndex = y;
      if (m_Debug) {
        int ci = (int) classIndex;
        System.err.println("\n\nClass " + m_Class.value(ci) + "(" + ci + "): "
          + aprioriDistribution[y] + "instances\n"
          + "=====================================\n");
      }

      if (Utils.eq(aprioriDistribution[y], 0.0)) {
        continue oneClass;
      }

      // The expected FP/err is the proportion of the class
      double expFPRate = (aprioriDistribution[y] / Utils
        .sum(aprioriDistribution));

      double classYWeights = 0, totalWeights = 0;
      for (int j = 0; j < data.numInstances(); j++) {
        Instance datum = data.instance(j);
        totalWeights += datum.weight();
        if ((int) datum.classValue() == y) {
          classYWeights += datum.weight();
        }
      }

      // DL of default rule, no theory DL, only data DL
      double defDL;
      if (classYWeights > 0) {
        defDL = RuleStats.dataDL(expFPRate, 0.0, totalWeights, 0.0,
          classYWeights);
      } else {
        continue oneClass; // Subsumed by previous rules
      }

      if (Double.isNaN(defDL) || Double.isInfinite(defDL)) {
        throw new Exception("Should never happen: " + "defDL NaN or infinite!");
      }
      if (m_Debug) {
        System.err.println("The default DL = " + defDL);
      }

      rulesetForOneClass(expFPRate, data, classIndex, defDL);
    }

    // Remove redundant antecedents
    for (int z = 0; z < m_Ruleset.size(); z++) {
      RipperRule rule = (RipperRule) m_Ruleset.get(z);
      for (int j = 0; j < rule.m_Antds.size(); j++) {
        Antd outerAntd = rule.m_Antds.get(j);
        for (int k = j + 1; k < rule.m_Antds.size(); k++) {
          Antd innerAntd = rule.m_Antds.get(k);
          if (outerAntd.att.index() == innerAntd.att.index()
            && outerAntd.value == innerAntd.value) {
            rule.m_Antds.set(j, rule.m_Antds.get(k));
            rule.m_Antds.remove(k--);
          }
        }
      }
    }

    // Fuzzify all rules
    for (int z = 0; z < m_RulesetStats.size(); z++) {
      RuleStats oneClass = m_RulesetStats.get(z);
      for (int xyz = 0; xyz < oneClass.getRulesetSize(); xyz++) {
        RipperRule rule = (RipperRule) (oneClass.getRuleset()).get(xyz);

        // do the fuzzification for all known antecedents
        rule.fuzzify(data, allWeightsAreOne);

        double[] classDist = oneClass.getDistributions(xyz);
        // Check for sum=0, because otherwise it does not work
        if (Utils.sum(classDist) > 0) {
          Utils.normalize(classDist);
        }
        if (classDist != null) {
          m_Distributions.add(classDist);
        }
      }
    }

    // if there was some problem during fuzzification, set the support bound
    // to the trivial fuzzification position
    for (int z = 0; z < m_Ruleset.size(); z++) {
      RipperRule rule = (RipperRule) m_Ruleset.get(z);
      for (int j = 0; j < rule.m_Antds.size(); j++) {
        Antd antd = rule.m_Antds.get(j);
        if (antd instanceof NumericAntd) {
          NumericAntd numAntd = (NumericAntd) antd;

          if (!numAntd.fuzzyYet) {
            for (int i = 0; i < data.numInstances(); i++) {
              if ((numAntd.value == 1
                && numAntd.splitPoint > data.instance(i).value(
                  numAntd.att.index()) && (numAntd.supportBound < data
                .instance(i).value(numAntd.att.index()) || !numAntd.fuzzyYet))
                || (numAntd.value == 0
                  && numAntd.splitPoint < data.instance(i).value(
                    numAntd.att.index()) && (numAntd.supportBound > data
                  .instance(i).value(numAntd.att.index()) || !numAntd.fuzzyYet))) {
                numAntd.supportBound = data.instance(i).value(
                  numAntd.att.index());
                numAntd.fuzzyYet = true;
              }
            }

          }
        }
      }
    }

    // Determine confidences
    for (int z = 0; z < m_Ruleset.size(); z++) {
      RipperRule rule = (RipperRule) m_Ruleset.get(z);
      rule.calculateConfidences(data);
    }
  }

  /**
   * Classify the test instance with the rule learner and provide the class
   * distributions
   * 
   * @param datum the instance to be classified
   * @return the distribution
   * @throws Exception
   */

  @Override
  public double[] distributionForInstance(Instance datum) throws Exception {
    // test for multiple overlap of rules
    double[] rulesCoveringForEachClass = new double[datum.numClasses()];
    for (int i = 0; i < m_Ruleset.size(); i++) {
      RipperRule rule = (RipperRule) m_Ruleset.get(i);

      /*
       * In case that one class does not contain any instances (e.g. in
       * UCI-dataset glass), a default rule assigns all instances to the other
       * class. Such a rule may be ignored here.
       */
      if (!rule.hasAntds()) {
        continue;
      }

      // Calculate the maximum degree of coverage
      if (rule.covers(datum)) {
        rulesCoveringForEachClass[(int) rule.m_Consequent] += rule
          .coverageDegree(datum) * rule.getConfidence();
      }

    }

    // If no rule covered the example, then maybe start the rule stretching
    if (Utils.sum(rulesCoveringForEachClass) == 0) {

      // If rule stretching is not allowed,
      // return either the apriori prediction
      if (m_uncovAction == UNCOVACTION_APRIORI) {
        rulesCoveringForEachClass = aprioriDistribution;
        if (Utils.sum(rulesCoveringForEachClass) > 0) {
          Utils.normalize(rulesCoveringForEachClass);
        }
        return rulesCoveringForEachClass;
      }
      // or abstain from that decision at all.
      if (m_uncovAction == UNCOVACTION_REJECT) {
        return rulesCoveringForEachClass;
      }

      // Copy the ruleset as backup
      ArrayList<Rule> origRuleset = new ArrayList<Rule>();
      for (Rule r : m_Ruleset) {
        origRuleset.add((Rule) r.copy());
      }

      // Find for every rule the first antecedent that does not
      // cover the given instance.
      rulesCoveringForEachClass = new double[rulesCoveringForEachClass.length];
      for (int i = 0; i < m_Ruleset.size(); i++) {
        RipperRule rule = (RipperRule) m_Ruleset.get(i);
        double numAntdsBefore = rule.m_Antds.size();

        int firstAntdToDelete = Integer.MAX_VALUE;
        for (int j = 0; j < rule.m_Antds.size(); j++) {
          if (rule.m_Antds.get(j).covers(datum) == 0) {
            firstAntdToDelete = j;
            break;
          }
        }

        // Prune antecedent such that it covers the instance
        for (int j = firstAntdToDelete; j < rule.m_Antds.size(); j++) {
          rule.m_Antds.remove(j--);
        }
        double numAntdsAfter = rule.m_Antds.size();

        // Empty rules shall not vote here
        if (!rule.hasAntds()) {
          continue;
        }

        // Calculate the maximum degree of coverage and weight the rule
        // by its confidence and the fraction of antecedents left after
        // rule stretching
        double secondWeight = (numAntdsAfter + 1) / (numAntdsBefore + 2);
        if (rule.getConfidence() * secondWeight * rule.coverageDegree(datum) >= rulesCoveringForEachClass[(int) rule
          .getConsequent()]) {
          rulesCoveringForEachClass[(int) rule.getConsequent()] = rule
            .getConfidence() * secondWeight * rule.coverageDegree(datum);
        }
      }

      // Reestablish original ruleset
      m_Ruleset = origRuleset;
    }

    // check for conflicts
    double[] maxClasses = new double[rulesCoveringForEachClass.length];
    for (int i = 0; i < rulesCoveringForEachClass.length; i++) {
      if (rulesCoveringForEachClass[Utils.maxIndex(rulesCoveringForEachClass)] == rulesCoveringForEachClass[i]
        && rulesCoveringForEachClass[i] > 0) {
        maxClasses[i] = 1;
      }
    }

    // If there is a conflict, resolve it using the apriori distribution
    if (Utils.sum(maxClasses) > 0) {
      for (int i = 0; i < maxClasses.length; i++) {
        if (maxClasses[i] > 0
          && aprioriDistribution[i] != rulesCoveringForEachClass[Utils
            .maxIndex(rulesCoveringForEachClass)]) {
          rulesCoveringForEachClass[i] -= 0.00001;
        }
      }
    }

    // If no stretched rule was able to cover the instance,
    // then fall back to the apriori distribution
    if (Utils.sum(rulesCoveringForEachClass) == 0) {
      rulesCoveringForEachClass = aprioriDistribution;
    }

    if (Utils.sum(rulesCoveringForEachClass) > 0) {
      Utils.normalize(rulesCoveringForEachClass);
    }

    return rulesCoveringForEachClass;

  }

  /**
   * Build a ruleset for the given class according to the given data
   * 
   * @param expFPRate the expected FP/(FP+FN) used in DL calculation
   * @param data the given data
   * @param classIndex the given class index
   * @param defDL the default DL in the data
   * @throws Exception if the ruleset can be built properly
   */
  protected Instances rulesetForOneClass(double expFPRate, Instances data,
    double classIndex, double defDL) throws Exception {

    Instances newData = data, growData, pruneData;
    boolean stop = false;
    ArrayList<Rule> ruleset = new ArrayList<Rule>();

    double dl = defDL, minDL = defDL;
    RuleStats rstats = null;
    double[] rst;

    // Check whether data have positive examples
    boolean defHasPositive = true; // No longer used
    boolean hasPositive = defHasPositive;

    /********************** Building stage ***********************/
    if (m_Debug) {
      System.err.println("\n*** Building stage ***");
    }

    while ((!stop) && hasPositive) { // Generate new rules until
      // stopping criteria met
      RipperRule oneRule;

      oneRule = new RipperRule();
      oneRule.setConsequent(classIndex); // Must set first
      if (m_Debug) {
        System.err.println("\nNo pruning: growing a rule ...");
      }
      oneRule.grow(newData); // Build the rule
      if (m_Debug) {
        System.err.println("No pruning: one rule found:\n"
          + oneRule.toString(m_Class));
      }

      // Compute the DL of this ruleset
      if (rstats == null) { // First rule
        rstats = new RuleStats();
        rstats.setNumAllConds(m_Total);
        rstats.setData(newData);
      }

      rstats.addAndUpdate(oneRule);
      int last = rstats.getRuleset().size() - 1; // Index of last rule
      dl += rstats.relativeDL(last, expFPRate, m_CheckErr);

      if (Double.isNaN(dl) || Double.isInfinite(dl)) {
        throw new Exception("Should never happen: dl in "
          + "building stage NaN or infinite!");
      }
      if (m_Debug) {
        System.err.println("Before optimization(" + last + "): the dl = " + dl
          + " | best: " + minDL);
      }

      if (dl < minDL) {
        minDL = dl; // The best dl so far
      }

      rst = rstats.getSimpleStats(last);
      if (m_Debug) {
        System.err.println("The rule covers: " + rst[0] + " | pos = " + rst[2]
          + " | neg = " + rst[4] + "\nThe rule doesn't cover: " + rst[1]
          + " | pos = " + rst[5]);
      }

      stop = checkStop(rst, minDL, dl);

      if (!stop) {
        ruleset.add(oneRule); // Accepted
        newData = rstats.getFiltered(last)[1];// Data not covered
        hasPositive = Utils.gr(rst[5], 0.0); // Positives remaining?
        if (m_Debug) {
          System.err.println("One rule added: has positive? " + hasPositive);
        }
      } else {
        if (m_Debug) {
          System.err.println("Quit rule");
        }
        rstats.removeLast(); // Remove last to be re-used
      }
    }// while !stop

    /******************** Optimization stage *******************/

    RuleStats finalRulesetStat = null;
    for (int z = 0; z < m_Optimizations; z++) {
      if (m_Debug) {
        System.err.println("\n*** Optimization: run #" + z + " ***");
      }

      newData = data;
      finalRulesetStat = new RuleStats();
      finalRulesetStat.setData(newData);
      finalRulesetStat.setNumAllConds(m_Total);
      int position = 0;
      stop = false;
      boolean isResidual = false;
      hasPositive = defHasPositive;
      dl = minDL = defDL;

      oneRule: while (!stop && hasPositive) {

        isResidual = (position >= ruleset.size()); // Cover residual positive
                                                   // examples
        // Re-do shuffling and stratification
        // newData.randomize(m_Random);
        newData = RuleStats.stratify(newData, m_Folds, m_Random);
        Instances[] part = RuleStats.partition(newData, m_Folds);
        growData = part[0];
        pruneData = part[1];
        // growData=newData.trainCV(m_Folds, m_Folds-1);
        // pruneData=newData.testCV(m_Folds, m_Folds-1);
        RipperRule finalRule;

        if (m_Debug) {
          System.err.println("\nRule #" + position + "| isResidual?"
            + isResidual + "| data size: " + newData.sumOfWeights());
        }

        if (isResidual) {
          RipperRule newRule = new RipperRule();
          newRule.setConsequent(classIndex);
          if (m_Debug) {
            System.err.println("\nGrowing and pruning" + " a new rule ...");
          }
          newRule.grow(newData);
          finalRule = newRule;
          if (m_Debug) {
            System.err
              .println("\nNew rule found: " + newRule.toString(m_Class));
          }
        } else {
          RipperRule oldRule = (RipperRule) ruleset.get(position);
          boolean covers = false;
          // Test coverage of the next old rule
          for (int i = 0; i < newData.numInstances(); i++) {
            if (oldRule.covers(newData.instance(i))) {
              covers = true;
              break;
            }
          }

          if (!covers) {// Null coverage, no variants can be generated
            finalRulesetStat.addAndUpdate(oldRule);
            position++;
            continue oneRule;
          }

          // 2 variants
          if (m_Debug) {
            System.err.println("\nGrowing and pruning" + " Replace ...");
          }
          RipperRule replace = new RipperRule();
          replace.setConsequent(classIndex);
          replace.grow(growData);

          // Remove the pruning data covered by the following
          // rules, then simply compute the error rate of the
          // current rule to prune it. According to Ripper,
          // it's equivalent to computing the error of the
          // whole ruleset -- is it true?
          pruneData = RuleStats.rmCoveredBySuccessives(pruneData, ruleset,
            position);
          replace.prune(pruneData, true);

          if (m_Debug) {
            System.err.println("\nGrowing and pruning" + " Revision ...");
          }
          RipperRule revision = (RipperRule) oldRule.copy();

          // For revision, first rm the data covered by the old rule
          Instances newGrowData = new Instances(growData, 0);
          for (int b = 0; b < growData.numInstances(); b++) {
            Instance inst = growData.instance(b);
            if (revision.covers(inst)) {
              newGrowData.add(inst);
            }
          }
          revision.grow(newGrowData);
          revision.prune(pruneData, true);

          double[][] prevRuleStats = new double[position][6];
          for (int c = 0; c < position; c++) {
            prevRuleStats[c] = finalRulesetStat.getSimpleStats(c);
          }

          // Now compare the relative DL of variants
          ArrayList<Rule> tempRules = new ArrayList<Rule>();
          for (Rule r : ruleset) {
            tempRules.add((Rule) r.copy());
          }
          tempRules.set(position, replace);

          RuleStats repStat = new RuleStats(data, tempRules);
          repStat.setNumAllConds(m_Total);
          repStat.countData(position, newData, prevRuleStats);
          // repStat.countData();
          rst = repStat.getSimpleStats(position);
          if (m_Debug) {
            System.err.println("Replace rule covers: " + rst[0] + " | pos = "
              + rst[2] + " | neg = " + rst[4] + "\nThe rule doesn't cover: "
              + rst[1] + " | pos = " + rst[5]);
          }

          double repDL = repStat.relativeDL(position, expFPRate, m_CheckErr);

          if (m_Debug) {
            System.err.println("\nReplace: " + replace.toString(m_Class)
              + " |dl = " + repDL);
          }

          if (Double.isNaN(repDL) || Double.isInfinite(repDL)) {
            throw new Exception("Should never happen: repDL"
              + "in optmz. stage NaN or " + "infinite!");
          }

          tempRules.set(position, revision);
          RuleStats revStat = new RuleStats(data, tempRules);
          revStat.setNumAllConds(m_Total);
          revStat.countData(position, newData, prevRuleStats);
          // revStat.countData();
          double revDL = revStat.relativeDL(position, expFPRate, m_CheckErr);

          if (m_Debug) {
            System.err.println("Revision: " + revision.toString(m_Class)
              + " |dl = " + revDL);
          }

          if (Double.isNaN(revDL) || Double.isInfinite(revDL)) {
            throw new Exception("Should never happen: revDL"
              + "in optmz. stage NaN or " + "infinite!");
          }

          rstats = new RuleStats(data, ruleset);
          rstats.setNumAllConds(m_Total);
          rstats.countData(position, newData, prevRuleStats);
          // rstats.countData();
          double oldDL = rstats.relativeDL(position, expFPRate, m_CheckErr);

          if (Double.isNaN(oldDL) || Double.isInfinite(oldDL)) {
            throw new Exception("Should never happen: oldDL"
              + "in optmz. stage NaN or " + "infinite!");
          }
          if (m_Debug) {
            System.err.println("Old rule: " + oldRule.toString(m_Class)
              + " |dl = " + oldDL);
          }

          if (m_Debug) {
            System.err.println("\nrepDL: " + repDL + "\nrevDL: " + revDL
              + "\noldDL: " + oldDL);
          }

          if ((oldDL <= revDL) && (oldDL <= repDL)) {
            finalRule = oldRule; // Old the best
          } else if (revDL <= repDL) {
            finalRule = revision; // Revision the best
          } else {
            finalRule = replace; // Replace the best
          }
        }

        finalRulesetStat.addAndUpdate(finalRule);
        rst = finalRulesetStat.getSimpleStats(position);

        if (isResidual) {
          dl += finalRulesetStat.relativeDL(position, expFPRate, m_CheckErr);

          if (m_Debug) {
            System.err.println("After optimization: the dl" + "=" + dl
              + " | best: " + minDL);
          }

          if (dl < minDL) {
            minDL = dl; // The best dl so far
          }

          stop = checkStop(rst, minDL, dl);
          if (!stop) {
            ruleset.add(finalRule); // Accepted
          } else {
            finalRulesetStat.removeLast(); // Remove last to be re-used
            position--;
          }
        } else {
          ruleset.set(position, finalRule); // Accepted
        }

        if (m_Debug) {
          System.err.println("The rule covers: " + rst[0] + " | pos = "
            + rst[2] + " | neg = " + rst[4] + "\nThe rule doesn't cover: "
            + rst[1] + " | pos = " + rst[5]);
          System.err.println("\nRuleset so far: ");
          for (int x = 0; x < ruleset.size(); x++) {
            System.err.println(x + ": "
              + ((RipperRule) ruleset.get(x)).toString(m_Class));
          }
          System.err.println();
        }

        // Data not covered
        if (finalRulesetStat.getRulesetSize() > 0) {
          newData = finalRulesetStat.getFiltered(position)[1];
        }
        hasPositive = Utils.gr(rst[5], 0.0); // Positives remaining?
        position++;
      } // while !stop && hasPositive

      if (ruleset.size() > (position + 1)) { // Hasn't gone through yet
        for (int k = position + 1; k < ruleset.size(); k++) {
          finalRulesetStat.addAndUpdate(ruleset.get(k));
        }
      }
      if (m_Debug) {
        System.err.println("\nDeleting rules to decrease"
          + " DL of the whole ruleset ...");
      }
      finalRulesetStat.reduceDL(expFPRate, m_CheckErr);

      if (m_Debug) {
        int del = ruleset.size() - finalRulesetStat.getRulesetSize();
        System.err.println(del + " rules are deleted"
          + " after DL reduction procedure");
      }
      ruleset = finalRulesetStat.getRuleset();
      rstats = finalRulesetStat;

    } // For each run of optimization

    // Concatenate the ruleset for this class to the whole ruleset
    if (m_Debug) {
      System.err.println("\nFinal ruleset: ");
      for (int x = 0; x < ruleset.size(); x++) {
        System.err.println(x + ": "
          + ((RipperRule) ruleset.get(x)).toString(m_Class));
      }
      System.err.println();
    }

    m_Ruleset.addAll(ruleset);
    m_RulesetStats.add(rstats);

    return null;
  }

  /**
   * Check whether the stopping criterion meets
   * 
   * @param rst the statistic of the ruleset
   * @param minDL the min description length so far
   * @param dl the current description length of the ruleset
   * @return true if stop criterion meets, false otherwise
   */
  private boolean checkStop(double[] rst, double minDL, double dl) {

    if (dl > minDL + MAX_DL_SURPLUS) {
      if (m_Debug) {
        System.err.println("DL too large: " + dl + " | " + minDL);
      }
      return true;
    } else if (!Utils.gr(rst[2], 0.0)) {// Covered positives
      if (m_Debug) {
        System.err.println("Too few positives.");
      }
      return true;
    } else if ((rst[4] / rst[0]) >= 0.5) {// Err rate
      if (m_CheckErr) {
        if (m_Debug) {
          System.err.println("Error too large: " + rst[4] + "/" + rst[0]);
        }
        return true;
      } else {
        return false;
      }
    } else {// Not stops
      if (m_Debug) {
        System.err.println("Continue.");
      }
      return false;
    }
  }

  /**
   * Prints the all the rules of the rule learner.
   * 
   * @return a textual description of the classifier
   */
  @Override
  public String toString() {
    if (m_Ruleset == null) {
      return "FURIA: No model built yet.";
    }

    StringBuffer sb = new StringBuffer("FURIA rules:\n" + "===========\n\n");
    for (int j = 0; j < m_RulesetStats.size(); j++) {
      RuleStats rs = m_RulesetStats.get(j);
      ArrayList<Rule> rules = rs.getRuleset();
      for (int k = 0; k < rules.size(); k++) {
        sb.append(((RipperRule) rules.get(k)).toString(m_Class) + " (CF = "
          + Math.round(100.0 * ((RipperRule) rules.get(k)).getConfidence())
          / 100.0 + ")\n");
      }
    }
    if (m_Debug) {
      System.err.println("Inside m_Ruleset");
      for (int i = 0; i < m_Ruleset.size(); i++) {
        System.err.println(((RipperRule) m_Ruleset.get(i)).toString(m_Class));
      }
    }
    sb.append("\nNumber of Rules : " + m_Ruleset.size() + "\n");

    return sb.toString();
  }

  /**
   * Main method.
   * 
   * @param args the options for the classifier
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    runClassifier(new FURIA(), args);
  }

  /**
   * 
   */
  @Override
  public String getRevision() {
    return "$Revision$";
  }
}
