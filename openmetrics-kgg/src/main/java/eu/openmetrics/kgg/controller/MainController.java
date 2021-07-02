package eu.openmetrics.kgg.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.servlet.http.HttpSession;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import eu.openmetrics.kgg.service.Converter;
import gr.tuc.ifc.IfcModel;

@Controller
@RequestMapping("/")
public class MainController {
 
	private static final Logger log = LoggerFactory.getLogger(MainController.class);
	
	@Value("${kgg.directory}")
	public String directory;

	@Autowired
	private Converter converter;

	@GetMapping("/")
	public ModelAndView main(HttpSession httpSession) {
		log.info("main page");
		ModelAndView modelView = new ModelAndView();
		modelView.setViewName("page-main");
		return modelView;
	}

	@RequestMapping(value="/upload", method=RequestMethod.POST)
	public ResponseEntity<String> upload(@RequestParam("file") MultipartFile multipartFile, HttpSession httpSession) throws IOException {
		String fileName = FilenameUtils.getBaseName(multipartFile.getOriginalFilename());
		String fileNameWithExtension = FilenameUtils.getName(multipartFile.getOriginalFilename());
		Path folderPath = Paths.get(directory).toAbsolutePath().normalize();
		Files.createDirectories(folderPath);
		Path filePath = folderPath.resolve(fileNameWithExtension);
		InputStream fileInputStream = multipartFile.getInputStream();
		OutputStream fileOutputStream = Files.newOutputStream(filePath);		
		IOUtils.copy(fileInputStream,fileOutputStream);
		fileInputStream.close();
		fileOutputStream.close();
		IfcModel ifcModel = new IfcModel();
		ifcModel.readFile(multipartFile.getInputStream());
		File rdfFile = new File(directory + File.separator + fileName + ".ttl");
		rdfFile.createNewFile();
		FileOutputStream rdfFileOutputStream = new FileOutputStream(rdfFile, false);
		Model rdfModel = converter.convert(ifcModel);
		rdfModel.write(rdfFileOutputStream, "ttl");
		rdfFileOutputStream.close();
		return ResponseEntity.ok().body("ok");
	}
}