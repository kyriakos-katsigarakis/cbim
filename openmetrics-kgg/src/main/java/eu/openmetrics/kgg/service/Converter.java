package eu.openmetrics.kgg.service;

import java.util.Iterator;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import gr.tuc.ifc.IfcModel;
import gr.tuc.ifc4.IfcBuilding;
import gr.tuc.ifc4.IfcObjectDefinition;
import gr.tuc.ifc4.IfcProject;
import gr.tuc.ifc4.IfcRelAggregates;
import gr.tuc.ifc4.IfcSite;

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
			
			Iterator<IfcRelAggregates> projectRelAggregatesIterator = ifcProject.getIsDecomposedBy().iterator();
			if(projectRelAggregatesIterator.hasNext()) {
				IfcRelAggregates projectRelAggregates = projectRelAggregatesIterator.next();
				List<IfcObjectDefinition> relatedObjectList = projectRelAggregates.getRelatedObjects();
				for(IfcObjectDefinition relatedObject : relatedObjectList) {
					if(relatedObject instanceof IfcSite) {
						IfcSite ifcSite = (IfcSite) relatedObject;
						//
						//
					
						
					}else if (relatedObject instanceof IfcBuilding) {
						//
						
						
						
					}else {
						
						log.warn("usupported case");
						
					}
				}
			}
		}		
	}
}
