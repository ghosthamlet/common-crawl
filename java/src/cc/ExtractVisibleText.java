package cc;

import java.io.IOException;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import de.l3s.boilerpipe.extractors.ExtractorBase;
import de.l3s.boilerpipe.extractors.KeepEverythingWithMinKWordsExtractor;

public class ExtractVisibleText extends Configured implements Tool {

  public static void main(String args[]) throws Exception {
    ToolRunner.run(new ExtractVisibleText(), args);
  }
    
  public int run(String[] args) throws Exception {
        
    if (args.length!=2) {
      throw new RuntimeException("usage: "+getClass().getName()+" <input> <output>");
    }
    
    JobConf conf = new JobConf(getConf(), getClass());
    conf.setJobName(getClass().getName());
    
    conf.setOutputKeyClass(Text.class);
    conf.setOutputValueClass(Text.class);
    conf.set("mapred.output.compress", "true");
    conf.set("mapred.output.compression.type", "BLOCK");
    conf.set("mapred.output.compression.codec", "org.apache.hadoop.io.compress.GzipCodec");
    
    conf.setNumReduceTasks(0);
    
    conf.setInputFormat(SequenceFileInputFormat.class);
    conf.setMapperClass(ExtractVisibleTextMapper.class);    
    
    FileInputFormat.addInputPath(conf, new Path(args[0]));
    FileOutputFormat.setOutputPath(conf, new Path(args[1]));
    conf.setOutputFormat(SequenceFileOutputFormat.class);
    
    JobClient.runJob(conf);

    return 0;
  }

  
  public static class ExtractVisibleTextMapper extends MapReduceBase implements Mapper<Text,Text,Text,Text> {
    
    private ExtractorBase extractor = new KeepEverythingWithMinKWordsExtractor(5);
    
    public void map(Text header, Text html, OutputCollector<Text, Text> collector, Reporter reporter) throws IOException {
   
      try {
          
        // parse visible text
        String visibleText = extractor.getText(html.toString()).trim();        
        if (visibleText.isEmpty()) {
          reporter.getCounter("ExtractVisibleText", "no_content").increment(1);
          return;
        }
        if (visibleText.indexOf(" ")==-1) {
          reporter.getCounter("ExtractVisibleText", "no_spaces_in_text").increment(1);
          return;
        }
        
        reporter.getCounter("ExtractVisibleText", "has_visible_text").increment(1);
        collector.collect(header, new Text(visibleText));
      }      
      catch(Exception e) {        
        reporter.getCounter("ExtractVisibleText.exception", e.getClass().getSimpleName()).increment(1);
      }
      catch(StackOverflowError so) {
        // neko html parser (?)
        reporter.getCounter("ExtractVisibleText.exception", "stack_overflow (neko?)").increment(1);        
      }
      
    }   
    
  }
  
}
