package org.millburn.kioskserver.relations;

import java.io.File;
import org.millburn.kioskserver.LoadedMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The class that deal with setting up access relations
 *
 * @author Alex Kolodkin, Keming Fei
 */
@RestController
public class RelationController {
    private final LoadedMemory lm;

    @Autowired
    public RelationController(LoadedMemory lm) {
        this.lm = lm;
    }

    /**
     * This deals with the URL: http://......./uploadRelations?path=...
     * <p>
     * Uploads the new access relations from a specific file
     *
     * @param path the API level of the kiosk
     * @return a text response telling the user whether it was successful
     */
    @GetMapping("/uploadRelations")
    public ResponseEntity<String> uploadRelations(@RequestParam(value = "path") String path) {
        if(!this.lm.getAccessRelations().uploadNewRelations(new File(path))) {
            return new ResponseEntity<>(HttpStatusCode.valueOf(422));
        }
        return new ResponseEntity<>("Uploaded relations", HttpStatusCode.valueOf(200));
    }
}
