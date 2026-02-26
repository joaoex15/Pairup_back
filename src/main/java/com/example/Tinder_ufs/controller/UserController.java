package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.User;
import com.example.Tinder_ufs.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("users")
@AllArgsConstructor
public class UserController {
    private final UserService userService;


    @GetMapping
    public ResponseEntity<List<User>> getAllUsers(){
        return ResponseEntity.ok(userService.getAllUsers());
    }
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserbyid(@PathVariable String id){
        return ResponseEntity.ok(userService.findById(id));
    }
    @PostMapping
    public  ResponseEntity<User> creatUser(@RequestBody User user){

        return ResponseEntity.ok(userService.CreatUser(user));
    }
    @PutMapping("/{id}")
    public  ResponseEntity<User> UpdateUserById(@PathVariable String id,@RequestBody User user){

        user.setId(id);
        return  ResponseEntity.ok(userService.Update(user));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUserById(@PathVariable String id){
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

}
