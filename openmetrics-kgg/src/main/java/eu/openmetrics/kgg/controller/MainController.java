package eu.openmetrics.kgg.controller;

import javax.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/")
public class MainController {
 
	private static final Logger log = LoggerFactory.getLogger(MainController.class);
	
	@GetMapping("/")
	public ModelAndView main(HttpSession httpSession) {
		log.info("main page");
		ModelAndView modelView = new ModelAndView();
		modelView.setViewName("page-main");
		return modelView;
	}
}
