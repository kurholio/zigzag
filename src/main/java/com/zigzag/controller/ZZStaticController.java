package com.zigzag.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ZZStaticController {
	
	
	static String base;
	static String counter;

	@GetMapping("/market")
    public String marketForm() {
        return "redirect:/market.html";
    }

    @GetMapping("/pair/{base}/{counter}")
    public String pairPage(@org.springframework.web.bind.annotation.PathVariable String base,
                           @org.springframework.web.bind.annotation.PathVariable String counter) {
    	ZZStaticController.base = base;
    	ZZStaticController.counter  = counter;
        return "redirect:/pair.html?base=" + base + "&counter=" + counter;
    }
    
    public static String getCounter() {
    	return counter;
    }
    
    public static String getBase() {
    	return base;
    }
    
    public static String getPair() {
    	return base+"/"+counter;
    }
    
	
}
