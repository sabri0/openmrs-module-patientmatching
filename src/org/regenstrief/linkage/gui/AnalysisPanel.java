package org.regenstrief.linkage.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.regenstrief.linkage.analysis.AverageFrequencyAnalyzer;
import org.regenstrief.linkage.analysis.DataSourceAnalysis;
import org.regenstrief.linkage.analysis.EMAnalyzer;
import org.regenstrief.linkage.analysis.EntropyAnalyzer;
import org.regenstrief.linkage.analysis.MaximumEntropyAnalyzer;
import org.regenstrief.linkage.analysis.PairDataSourceAnalysis;
import org.regenstrief.linkage.analysis.RandomSampleAnalyzer;
import org.regenstrief.linkage.analysis.NullAnalyzer;
import org.regenstrief.linkage.analysis.UniqueAnalyzer;
import org.regenstrief.linkage.analysis.VectorTable;
import org.regenstrief.linkage.io.DedupOrderedDataSourceFormPairs;
import org.regenstrief.linkage.io.FormPairs;
import org.regenstrief.linkage.io.OrderedDataSourceFormPairs;
import org.regenstrief.linkage.io.OrderedDataSourceReader;
import org.regenstrief.linkage.io.ReaderProvider;
import org.regenstrief.linkage.util.LinkDataSource;
import org.regenstrief.linkage.util.MatchingConfig;
import org.regenstrief.linkage.util.RecMatchConfig;

/**
 * Class displays different analysis options available in the record linkage GUI
 * 
 * @author jegg
 *
 */

public class AnalysisPanel extends JPanel implements ActionListener{
	private static final long serialVersionUID = -6402375274052004924L;

	RecMatchConfig rm_conf;
	
	private JButton random_button;
	
	private JButton em_button, vector_button, summary_button;
	
	public AnalysisPanel(RecMatchConfig rmc){
		super();
		rm_conf = rmc;
		createAnalysisPanel();
	}
	
	public void setRecMatchConfig(RecMatchConfig rmc){
		rm_conf = rmc;
	}
	
	private void createAnalysisPanel(){
		//this.setLayout(new BorderLayout());
	    random_button = new JButton("Perform Random Sampling");
        this.add(random_button);
        random_button.addActionListener(this);
        
		em_button = new JButton("Perform EM Analysis");
		this.add(em_button);
		em_button.addActionListener(this);
		
		vector_button = new JButton("View score tables");
		this.add(vector_button);
		vector_button.addActionListener(this);
		
		summary_button = new JButton("Perform Summary Statistic Analyses");
		this.add(summary_button);
		summary_button.addActionListener(this);
	}
	
	private void runEMAnalysis(){
		ReaderProvider rp = new ReaderProvider();
		List<MatchingConfig> mcs = rm_conf.getMatchingConfigs();
		Iterator<MatchingConfig> it = mcs.iterator();
		while(it.hasNext()){
			MatchingConfig mc = it.next();
			
			OrderedDataSourceReader odsr1 = rp.getReader(rm_conf.getLinkDataSource1(), mc);
			OrderedDataSourceReader odsr2 = rp.getReader(rm_conf.getLinkDataSource2(), mc);
			if(odsr1 != null && odsr2 != null){
				// analyze with EM
			    FormPairs fp2 = null;
			    if (rm_conf.isDeduplication()) {
			        fp2 = new DedupOrderedDataSourceFormPairs(odsr1, mc, rm_conf.getLinkDataSource1().getTypeTable());
			    } else {
			        fp2 = new OrderedDataSourceFormPairs(odsr1, odsr2, mc, rm_conf.getLinkDataSource1().getTypeTable());
			    }
				/*
				 * Using two analyzer at a time in the PairDataSourceAnalysis. The order when adding the analyzer to
				 * PairDataSourceAnalysis will affect the end results. For example in the following code fragment,
				 * RandomSampleAnalyzer will be run first followed by the EMAnalyzer. But this will be depend on
				 * current Java's ArrayList implementation, if they add new element add the end of the list, then
				 * this will work fine.
				 * 
				 * In the following code, RandomSampleAnalyzer and EMAnalyzer will work independent each other.
				 * RandomSampleAnalyzer will generate the u value and save it in MatchingConfigRow object, while
				 * EMAnalyzer will check MatchingConfig to find out whether the blocking run use random sampling
				 * (where u value that will be used is the one generated by RandomSampleAnalyzer) or not using 
				 * random sampling (where u value will be the default value).
				 * 
				 * I don't think we need to instantiate the RandomSampleAnalyzer here if the user doesn't want to
				 * use random sampling :D
				 */
                LoggingFrame frame = new LoggingFrame(mc.getName());
				PairDataSourceAnalysis pdsa = new PairDataSourceAnalysis(fp2);
				// if not using random sampling then don't instantiate random sample analyzer
				// if the value is locked then don't instantiate random sampler analyzer
				if(mc.isUsingRandomSampling() && !mc.isLockedUValues()) {
					// create FormPairs for rsa to use
					OrderedDataSourceReader rsa_odsr1 = rp.getReader(rm_conf.getLinkDataSource1(), mc);
					OrderedDataSourceReader rsa_odsr2 = rp.getReader(rm_conf.getLinkDataSource2(), mc);
					FormPairs rsa_fp2 = null;
				    if (rm_conf.isDeduplication()) {
				        rsa_fp2 = new DedupOrderedDataSourceFormPairs(rsa_odsr1, mc, rm_conf.getLinkDataSource1().getTypeTable());
				    } else {
				        rsa_fp2 = new OrderedDataSourceFormPairs(rsa_odsr1, rsa_odsr2, mc, rm_conf.getLinkDataSource1().getTypeTable());
				    }
				    RandomSampleAnalyzer rsa = new RandomSampleAnalyzer(mc, rsa_fp2);
	                pdsa.addAnalyzer(rsa);
	                frame.addLoggingObject(rsa);
				}
				EMAnalyzer ema = new EMAnalyzer(mc);
				pdsa.addAnalyzer(ema);
				frame.addLoggingObject(ema);
				frame.configureLoggingFrame();
				pdsa.analyzeData();
			}
			odsr1.close();
			odsr2.close();
		}
	}
	
	private void displayVectorTables(){
		Iterator<MatchingConfig> it = rm_conf.getMatchingConfigs().iterator();
		while(it.hasNext()){
			MatchingConfig mc = it.next();
			VectorTable vt = new VectorTable(mc);
			TextDisplayFrame tdf = new TextDisplayFrame(mc.getName(), vt.toString());
		}
	}
	
	public void actionPerformed(ActionEvent ae){
		if(ae.getSource() == em_button){
			runEMAnalysis();
		} else if(ae.getSource() == vector_button){
			displayVectorTables();
		} else if(ae.getSource() == random_button) {
		    performRandomSampling();
		} else if(ae.getSource() == summary_button) {
			performSummaryStatistics();
		}
	}
	
	private void performSummaryStatistics() {
		ReaderProvider rp = new ReaderProvider();
		List<MatchingConfig> mcs = rm_conf.getMatchingConfigs();
		Iterator<MatchingConfig> it = mcs.iterator();
		while(it.hasNext()){
			MatchingConfig mc = it.next();
			
			LinkDataSource lds1 = rm_conf.getLinkDataSource1();
			LinkDataSource lds2 = rm_conf.getLinkDataSource2();
			
			OrderedDataSourceReader odsr1 = rp.getReader(lds1, mc);
			OrderedDataSourceReader odsr2 = rp.getReader(lds2, mc);

			if(lds1 != null && lds2 != null){
				// compute summary statistics
                LoggingFrame frame = new LoggingFrame(mc.getName());
				DataSourceAnalysis dsa1 = new DataSourceAnalysis(odsr1);
				DataSourceAnalysis dsa2 = new DataSourceAnalysis(odsr2);

				// Null - compute the number of null elements for each demographic
				NullAnalyzer na1 = new NullAnalyzer(lds1, mc);
				NullAnalyzer na2 = new NullAnalyzer(lds2, mc);
				
				dsa1.addAnalyzer(na1);
				dsa2.addAnalyzer(na2);
				/*
				frame.addLoggingObject(na1);
				frame.addLoggingObject(na2);
				*/ 
				
				// Entropy - compute the entropy of a demographic
				EntropyAnalyzer ea1 = new EntropyAnalyzer(lds1, mc);
				EntropyAnalyzer ea2 = new EntropyAnalyzer(lds2, mc);
				
				dsa1.addAnalyzer(ea1);
				dsa2.addAnalyzer(ea2);
				
				// Unique - compute the number of unique values of a demographic
				UniqueAnalyzer ua1 = new UniqueAnalyzer(lds1, mc);
				UniqueAnalyzer ua2 = new UniqueAnalyzer(lds2, mc);
				
				dsa1.addAnalyzer(ua1);
				dsa2.addAnalyzer(ua2);
				
				// Average Frequency - compute the average frequency of values in a demographic
				AverageFrequencyAnalyzer afa1 = new AverageFrequencyAnalyzer(lds1, mc, ua1.getResults());
				AverageFrequencyAnalyzer afa2 = new AverageFrequencyAnalyzer(lds2, mc, ua2.getResults());
				
				dsa1.addAnalyzer(afa1);
				dsa2.addAnalyzer(afa2);
				
				// Maximum Entropy - compute the maximum entropy of a demographic
				MaximumEntropyAnalyzer mea1 = new MaximumEntropyAnalyzer(lds1, mc, afa1.getResults(), ua1.getResults());
				MaximumEntropyAnalyzer mea2 = new MaximumEntropyAnalyzer(lds2, mc, afa2.getResults(), ua2.getResults());
				
				dsa1.addAnalyzer(mea1);
				dsa2.addAnalyzer(mea2);
				
				// Finish by configuring the frame and looping through all Analyzers
				frame.configureLoggingFrame();
				dsa1.analyzeData();
				dsa2.analyzeData();
			}
			odsr1.close();
			odsr2.close();
		}
	}

    private void performRandomSampling() {
        ReaderProvider rp = new ReaderProvider();
        List<MatchingConfig> mcs = rm_conf.getMatchingConfigs();
        Iterator<MatchingConfig> it = mcs.iterator();
        while(it.hasNext()){
            MatchingConfig mc = it.next();
            // if the user not choose to use random sampling, then do nothing
            // if the u-values is already locked then do nothing as well
            if(mc.isUsingRandomSampling() && !mc.isLockedUValues()) {
                OrderedDataSourceReader odsr1 = rp.getReader(rm_conf.getLinkDataSource1(), mc);
                OrderedDataSourceReader odsr2 = rp.getReader(rm_conf.getLinkDataSource2(), mc);
                if(odsr1 != null && odsr2 != null){
                    FormPairs fp2 = null;
                    if(rm_conf.isDeduplication()) {
                        fp2 = new DedupOrderedDataSourceFormPairs(odsr1, mc, rm_conf.getLinkDataSource1().getTypeTable());
                    } else {
                        fp2 = new OrderedDataSourceFormPairs(odsr1, odsr2, mc, rm_conf.getLinkDataSource1().getTypeTable());
                    }
                    
                    PairDataSourceAnalysis pdsa = new PairDataSourceAnalysis(fp2);
                    
                    RandomSampleLoggingFrame frame = new RandomSampleLoggingFrame(mc);
                    
                    MatchingConfig mcCopy = (MatchingConfig) mc.clone();
                    
                    OrderedDataSourceReader rsa_odsr1 = rp.getReader(rm_conf.getLinkDataSource1(), mc);
					OrderedDataSourceReader rsa_odsr2 = rp.getReader(rm_conf.getLinkDataSource2(), mc);
					FormPairs rsa_fp2 = null;
				    if (rm_conf.isDeduplication()) {
				        rsa_fp2 = new DedupOrderedDataSourceFormPairs(rsa_odsr1, mc, rm_conf.getLinkDataSource1().getTypeTable());
				    } else {
				        rsa_fp2 = new OrderedDataSourceFormPairs(rsa_odsr1, rsa_odsr2, mc, rm_conf.getLinkDataSource1().getTypeTable());
				    }
                    RandomSampleAnalyzer rsa = new RandomSampleAnalyzer(mcCopy, rsa_fp2);
                    
                    pdsa.addAnalyzer(rsa);
                    frame.addLoggingObject(rsa);
                    
                    frame.configureLoggingFrame();
                    pdsa.analyzeData();
                }
                odsr1.close();
                odsr2.close();
            }
        }
    }
}
