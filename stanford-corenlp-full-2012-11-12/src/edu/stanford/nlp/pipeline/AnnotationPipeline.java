package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * This class is designed to apply multiple Annotators
 * to an Annotation.  The idea is that you first
 * build up the pipeline by adding Annotators, and then
 * you takes the objects you wish to annotate and pass
 * them in and get in return a fully annotated object.
 * Please see package level javadocs for sample usage
 * and a more complete description.
 * <p>
 * At the moment this mainly serves as an example of using
 * the system and actually more complex annotation pipelines are
 * in their own classes that don't extend this one.
 *
 * @author Jenny Finkel
 */

public class AnnotationPipeline implements Annotator {

  protected static final boolean TIME = true;

  private List<Annotator> annotators;
  private List<MutableInteger> accumulatedTime;

  public AnnotationPipeline(List<Annotator> annotators) {
    this.annotators = annotators;
    if (TIME) {
      int num = annotators.size();
      accumulatedTime = new ArrayList<MutableInteger>(annotators.size());
      for (int i = 0; i < num; i++) {
        accumulatedTime.add(new MutableInteger());
      }
    }
  }

  public AnnotationPipeline() {
    this(new ArrayList<Annotator>());
  }

  public void addAnnotator(Annotator annotator) {
    annotators.add(annotator);
    if (TIME) {
      accumulatedTime.add(new MutableInteger());
    }
  }

  /**
   * Run the pipeline on an input annotation.
   * The annotation is modified in place
   * @param annotation The input annotation, usually a raw document
   */
  public void annotate(Annotation annotation) {
    Iterator<MutableInteger> it = accumulatedTime.iterator();
    Timing t = new Timing();
    for (Annotator annotator : annotators) {
      if (TIME) {
        t.start();
      }
      annotator.annotate(annotation);
      if (TIME) {
        int elapsed = (int) t.stop();
        MutableInteger m = it.next();
        m.incValue(elapsed);
      }
    }
  }

  /**
   * Annotate a collection of input annotations IN PARALLEL, making use of
   * all available cores.
   * @param annotations The input annotations to process
   */
  public void annotate(Iterable<Annotation> annotations){
    annotate(annotations, Runtime.getRuntime().availableProcessors());
  }

	/**
	 * Annotate a collection of input annotations IN PARALLEL, making use of
	 * all available cores
	 * @param annotations The input annotations to process
	 * @param callback A function to be called when an annotation finishes. The return value of the callback is ignored
	 */
  public void annotate(final Iterable<Annotation> annotations, final Function<Annotation,Object> callback){
    annotate(annotations, Runtime.getRuntime().availableProcessors(), callback);
  }

	/**
	 * Annotate a collection of input annotations IN PARALLEL, making use of
	 * threads given in numThreads
	 * @param annotations The input annotations to process
	 * @param numThreads The number of threads to run on
	 */
  public void annotate(final Iterable<Annotation> annotations, int numThreads){
    annotate(annotations, numThreads, new Function<Annotation, Object>() {
      public Object apply(Annotation in) { return null; }
    });
  }

  /**
   * Annotate a collection of input annotations IN PARALLEL, making use of
   * threads given in numThreads
   * @param annotations The input annotations to process
   * @param numThreads The number of threads to run on
	 * @param callback A function to be called when an annotation finishes.
	 *                 The return value of the callback is ignored.
   */
  public void annotate(final Iterable<Annotation> annotations, int numThreads, final Function<Annotation,Object> callback){
    // case: single thread (no point in spawning threads)
    if(numThreads == 1){
      for(Annotation ann : annotations){
        annotate(ann);
        callback.apply(ann);
      }
    }
    // Java's equivalent to ".map{ lambda(annotation) => annotate(annotation) }
    Iterable<Runnable> threads = new Iterable<Runnable>(){
      public Iterator<Runnable> iterator() {
        final Iterator<Annotation> iter = annotations.iterator();
        return new Iterator<Runnable>(){
          public boolean hasNext() {
            return iter.hasNext();
          }
          public Runnable next() {
            final Annotation input = iter.next();
            return new Runnable(){
              public void run(){
                //Jesus Christ, finally the body of the code
                //(logging)
                String beginningOfDocument = input.toString().substring(0,Math.min(50,input.toString().length()));
                Redwood.startTrack("Annotating \"" + beginningOfDocument + "...\"");
                //(annotate)
                annotate(input);
                //(callback)
                callback.apply(input);
                //(logging again)
                Redwood.endTrack("Annotating \"" + beginningOfDocument + "...\"");
              }
            };
          }
          public void remove() {
            iter.remove();
          }
        };
      }
    };
    // Thread
    Redwood.Util.threadAndRun(this.getClass().getSimpleName(), threads, numThreads );
  }

  /** Return the total pipeline annotation time in milliseconds.
   *
   *  @return The total pipeline annotation time in milliseconds
   */
  protected long getTotalTime() {
    long total = 0;
    for (MutableInteger m: accumulatedTime) {
      total += m.longValue();
    }
    return total;
  }

  /** Return a String that gives detailed human-readable information about
   *  how much time was spent by each annotator and by the entire annotation
   *  pipeline.  This String includes newline characters but does not end
   *  with one, and so it is suitable to be printed out with a 
   *  <code>println()</code>.
   *
   *  @return Human readable information on time spent in processing.
   */
  public String timingInformation() {
    StringBuilder sb = new StringBuilder();
    if (TIME) {
      sb.append("Annotation pipeline timing information:\n");
      Iterator<MutableInteger> it = accumulatedTime.iterator();
      long total = 0;
      for (Annotator annotator : annotators) {
        MutableInteger m = it.next();
        sb.append(StringUtils.getShortClassName(annotator)).append(": ");
        sb.append(Timing.toSecondsString(m.longValue())).append(" sec.\n");
        total += m.longValue();
      }
      sb.append("TOTAL: ").append(Timing.toSecondsString(total)).append(" sec.");
    }
    return sb.toString();
  }


  public static void main(String[] args) throws IOException, ClassNotFoundException {
    Timing tim = new Timing();
    AnnotationPipeline ap = new AnnotationPipeline();
    boolean verbose = false;
    ap.addAnnotator(new PTBTokenizerAnnotator(verbose));
    ap.addAnnotator(new WordsToSentencesAnnotator(verbose));
    // ap.addAnnotator(new NERCombinerAnnotator(verbose));
    // ap.addAnnotator(new OldNERAnnotator(verbose));
    // ap.addAnnotator(new NERMergingAnnotator(verbose));
    ap.addAnnotator(new ParserAnnotator(verbose, -1));
/**
    ap.addAnnotator(new UpdateSentenceFromParseAnnotator(verbose));
    ap.addAnnotator(new NumberAnnotator(verbose));
    ap.addAnnotator(new QuantifiableEntityNormalizingAnnotator(verbose));
    ap.addAnnotator(new StemmerAnnotator(verbose));
    ap.addAnnotator(new MorphaAnnotator(verbose));
**/
//    ap.addAnnotator(new SRLAnnotator());

    String text = ("USAir said in the filings that Mr. Icahn first contacted Mr. Colodny last September to discuss the benefits of combining TWA and USAir -- either by TWA's acquisition of USAir, or USAir's acquisition of TWA.");
    Annotation a = new Annotation(text);
    ap.annotate(a);
    System.out.println(a.get(TokensAnnotation.class));
    for (CoreMap sentence : a.get(SentencesAnnotation.class)) {
      System.out.println(sentence.get(TreeAnnotation.class));
    }

    if (TIME) {
      System.out.println(ap.timingInformation());
      System.err.println("Total time for AnnotationPipeline: " +
                         tim.toSecondsString() + " sec.");
    }
  }

}
