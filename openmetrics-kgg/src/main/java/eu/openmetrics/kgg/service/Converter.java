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
import gr.tuc.ifc4.IfcBuildingStorey;
import gr.tuc.ifc4.IfcObjectDefinition;
import gr.tuc.ifc4.IfcProduct;
import gr.tuc.ifc4.IfcProject;
import gr.tuc.ifc4.IfcRelAggregates;
import gr.tuc.ifc4.IfcRelContainedInSpatialStructure;
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
				List<IfcObjectDefinition> projectRelatedObjectList = projectRelAggregatesIterator.next().getRelatedObjects();
				for(IfcObjectDefinition projectRelatedObject : projectRelatedObjectList) {
					if(projectRelatedObject instanceof IfcSite) {
						IfcSite ifcSite = (IfcSite) projectRelatedObject;
						//
						//
						Iterator<IfcRelAggregates> siteRelAggregatesIterator = ifcSite.getIsDecomposedBy().iterator();
						if(siteRelAggregatesIterator.hasNext()) {
							List<IfcObjectDefinition> siteRelatedObjectList = siteRelAggregatesIterator.next().getRelatedObjects();
							for(IfcObjectDefinition siteRelatedObject : siteRelatedObjectList) {
								if(siteRelatedObject instanceof IfcBuilding) {
									IfcBuilding ifcBuilding = (IfcBuilding) projectRelatedObject;
											
								}else {
									log.warn("unsupported case");
								}
							}
						}
						
						
									
					}else if (projectRelatedObject instanceof IfcBuilding) {
						IfcBuilding ifcBuilding = (IfcBuilding) projectRelatedObject;
						//
						//
						
						
						
					}else {
						log.warn("usupported case");
					}
				}
			}
		}		
	}
	
	private void parseBuilding(IfcBuilding ifcBuilding) {
		//
		//
		Iterator<IfcRelAggregates> buildingRelAggregatesIterator = ifcBuilding.getIsDecomposedBy().iterator();
		if(buildingRelAggregatesIterator.hasNext()) {
			List<IfcObjectDefinition> buildingRelatedObjectList = buildingRelAggregatesIterator.next().getRelatedObjects();
			for(IfcObjectDefinition builidngRelatedObject : buildingRelatedObjectList) {
				if(builidngRelatedObject instanceof IfcBuildingStorey) {
					IfcBuildingStorey buildingStorey = (IfcBuildingStorey) builidngRelatedObject;
					//
					// 1. using decomposedBy
					Iterator<IfcRelAggregates> buildingStoreyRelAggregatesIterator = buildingStorey.getIsDecomposedBy().iterator(); 
					if(buildingStoreyRelAggregatesIterator.hasNext()) {
						List<IfcObjectDefinition> buildingStoreyRelatedObjectList = buildingStoreyRelAggregatesIterator.next().getRelatedObjects();
						for(IfcObjectDefinition buildingStoreyRelatedObject : buildingStoreyRelatedObjectList) {
							//
							//
							if(buildingStoreyRelatedObject instanceof IfcProduct) {
								IfcProduct product = (IfcProduct) buildingStoreyRelatedObject;
								//
								//
								
							}else {
								// we exclude IfcActor, IfcControl, IfcProcess etc.
								log.warn("unsupported case");
							}
						}
					}
					// 2. using containedInSpatialStructure
					Iterator<IfcRelContainedInSpatialStructure> buildingStoreyContainedInSpatialStructureIterator = buildingStorey.getContainsElements().iterator();
					while(buildingStoreyContainedInSpatialStructureIterator.hasNext()) {
						IfcRelContainedInSpatialStructure relContainedInSpatialStructure = buildingStoreyContainedInSpatialStructureIterator.next();
						for(IfcProduct product : relContainedInSpatialStructure.getRelatedElements()) {
							//
							//
							
						}
					}					
				}else {
					log.warn("unsupported case");
				}
			}
		}
	}
	
}
