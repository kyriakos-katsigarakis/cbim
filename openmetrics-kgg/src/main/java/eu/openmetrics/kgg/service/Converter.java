package eu.openmetrics.kgg.service;

import java.util.Iterator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import gr.tuc.ifc.IfcModel;
import gr.tuc.ifc2x3tc1.IfcProject;

@Service
public class Converter {

	private static Logger log = LoggerFactory.getLogger(Converter.class);
		
	public Converter() {
		log.info("init ifc to knowledge graph converter");
	}
	
	
	public void convert(IfcModel ifcModel) {
		Model rdfModel = ModelFactory.createDefaultModel();
		
		
		Iterator<IfcProject> projectIterator = ifcModel.getAllE(IfcProject.class).iterator();
		if(projectIterator.hasNext()){
			IfcProject ifcProject = projectIterator.next();
			//
			//
			
			
			
		}
		
				
		
	}
}
