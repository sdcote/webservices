package com.sdcote.profilesvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sdcote.profilesvc.model.Greeting;

@RestController
@RequestMapping("/v1/profile")
@Tag(name = "Demo Service", description = "Operations related to the demo service")
public class ProfileController {


    /**
     * Greets the user with a default message or a personalized one.
     * @param name The name to greet (optional).
     * @return A greeting message.
     */
    @Operation(summary = "Get a greeting message", description = "Returns a personalized greeting if a name is provided, otherwise a default greeting.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved greeting"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @GetMapping("/greet")
    public ResponseEntity<Greeting> greet(
            @Parameter(description = "Name of the person to greet", example = "World")
            @RequestParam(value = "name", defaultValue = "World") String name) {
        return ResponseEntity.ok(new Greeting("Hello, " + name + "!"));
    }




    /**
     * Returns a message with the provided ID.
     * @param id The ID of the message.
     * @return A message with the given ID.
     */
    @Operation(summary = "Get a message by ID", description = "Retrieves a specific message using its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved message"),
            @ApiResponse(responseCode = "404", description = "Message not found")
    })
    @GetMapping("/message/{id}")
    public ResponseEntity<Greeting> getMessageById(
            @Parameter(description = "ID of the message to retrieve", example = "123")
            @PathVariable String id) {
        // In a real application, you would fetch this from a database
        return ResponseEntity.ok(new Greeting("You requested message with ID: " + id));
    }
    
}
