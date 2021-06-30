package eu.openmetrics.kgg.service;

import java.util.Iterator;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import gr.tuc.ifc.IfcModel;
import gr.tuc.ifc4.IfcBeam;
import gr.tuc.ifc4.IfcBuilding;
import gr.tuc.ifc4.IfcBuildingStorey;
import gr.tuc.ifc4.IfcColumn;
import gr.tuc.ifc4.IfcDoor;
import gr.tuc.ifc4.IfcObjectDefinition;
import gr.tuc.ifc4.IfcOpeningElement;
import gr.tuc.ifc4.IfcProduct;
import gr.tuc.ifc4.IfcProject;
import gr.tuc.ifc4.IfcRelAggregates;
import gr.tuc.ifc4.IfcRelContainedInSpatialStructure;
import gr.tuc.ifc4.IfcRelFillsElement;
import gr.tuc.ifc4.IfcRelVoidsElement;
import gr.tuc.ifc4.IfcRoof;
import gr.tuc.ifc4.IfcSite;
import gr.tuc.ifc4.IfcSlab;
import gr.tuc.ifc4.IfcSpace;
import gr.tuc.ifc4.IfcWall;
import gr.tuc.ifc4.IfcWallStandardCase;
import gr.tuc.ifc4.IfcWindow;

@Service
public class Converter {

	private static Logger log = LoggerFactory.getLogger(Converter.class);
		
	public Converter() {
		log.info("init ifc to knowledge graph converter");
	}
		
	public void convert(IfcModel ifcModel) {
		Model rdfModel = ModelFactory.createDefaultModel();
		rdfModel.setNsPrefix("om", "http://openmetrics.eu/openmetrics#");
		rdfModel.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
		rdfModel.setNsPrefix("rdf", RDF.uri);
		rdfModel.setNsPrefix("rdfs", RDFS.uri);
		rdfModel.setNsPrefix("schema", "http://schema.org#");
		rdfModel.setNsPrefix("brick", "https://brickschema.org/schema/1.1/Brick#");
		
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
									IfcBuilding building = (IfcBuilding) projectRelatedObject;
									parseBuilding(building);
								}else {
									log.warn("unsupported case");
								}
							}
						}		
					}else if (projectRelatedObject instanceof IfcBuilding) {
						IfcBuilding building = (IfcBuilding) projectRelatedObject;
						parseBuilding(building);					
					}else {
						log.warn("usupported case");
					}
				}
			}
		}		
	}
	
	private void parseBuilding(IfcBuilding building) {
		// 1. using containedInSpatialStructure
		Iterator<IfcRelContainedInSpatialStructure> buildingContainedInSpatialStructureIterator = building.getContainsElements().iterator();
		while(buildingContainedInSpatialStructureIterator.hasNext()) {
			IfcRelContainedInSpatialStructure relContainedInSpatialStructure = buildingContainedInSpatialStructureIterator.next();
			for(IfcProduct product : relContainedInSpatialStructure.getRelatedElements()) {
				// -->
				parseProduct(product);
			}
		}
		// 2. using decomposedBy
		Iterator<IfcRelAggregates> buildingRelAggregatesIterator = building.getIsDecomposedBy().iterator();
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
							if(buildingStoreyRelatedObject instanceof IfcProduct) {
								IfcProduct product = (IfcProduct) buildingStoreyRelatedObject;
								// -->						
								parseProduct(product);
							}else {
								// we exclude some entities
								log.warn("unsupported case");
							}
						}
					}
					// 2. using containedInSpatialStructure
					Iterator<IfcRelContainedInSpatialStructure> buildingStoreyContainedInSpatialStructureIterator = buildingStorey.getContainsElements().iterator();
					while(buildingStoreyContainedInSpatialStructureIterator.hasNext()) {
						IfcRelContainedInSpatialStructure relContainedInSpatialStructure = buildingStoreyContainedInSpatialStructureIterator.next();
						for(IfcProduct product : relContainedInSpatialStructure.getRelatedElements()) {
							// -->
							parseProduct(product);
						}
					}					
				}else {
					log.warn("unsupported case");
				}
			}
		}
	}
	
	private void parseProduct(IfcProduct product) {
		
		if(product instanceof IfcWall) {
			IfcWall ifcWall = (IfcWall) product;
			//
			//
			Iterator<IfcRelVoidsElement> wallOpeningIterator = ifcWall.getHasOpenings().iterator();
			if(wallOpeningIterator.hasNext()) {
				IfcRelVoidsElement relVoidsElement = wallOpeningIterator.next();
				if(relVoidsElement.getRelatedOpeningElement() instanceof IfcOpeningElement) {
					IfcOpeningElement opening = (IfcOpeningElement) relVoidsElement.getRelatedOpeningElement();
					// -->
					parseOpening(opening);
				}else {
					log.warn("unsupported case");
				}
			}
		}else if(product instanceof IfcWallStandardCase) {
			IfcWallStandardCase ifcWallStandardCase = (IfcWallStandardCase) product;
			//
			//
			
		}else if(product instanceof IfcSlab) {
			IfcSlab ifcSlab = (IfcSlab) product;
			//
			//
			
		}else if(product instanceof IfcSpace) {
			IfcSpace ifcSpace = (IfcSpace) product;
						
		}else if(product instanceof IfcColumn) {
			IfcColumn ifcColumn = (IfcColumn) product;
						
		}else if(product instanceof IfcBeam) {
			IfcBeam ifcBeam = (IfcBeam) product;
			
		}else if(product instanceof IfcRoof) {
			IfcRoof ifcRoof = (IfcRoof) product;			
			
			// 1. hasOpenings
			
			
			// 2. isDecomposedBy (e.g. slabs)
			
		}
	}
	
	public void parseOpening(IfcOpeningElement opening) {
		Iterator<IfcRelFillsElement> relFillsElementIterator = opening.getHasFillings().iterator();
		if(relFillsElementIterator.hasNext()) {
			IfcRelFillsElement relFillsElement = relFillsElementIterator.next();
			if(relFillsElement.getRelatedBuildingElement() instanceof IfcDoor) {
				IfcDoor door = (IfcDoor) relFillsElement.getRelatedBuildingElement();
				//
				//
				
			}else if(relFillsElement.getRelatedBuildingElement() instanceof IfcWindow) {				
				IfcWindow window = (IfcWindow) relFillsElement.getRelatedBuildingElement();
				//
				//
				
			}else {
				log.warn("unsupported case");
			}
		}
	}
}
