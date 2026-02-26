package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.models.User;
import com.example.Tinder_ufs.repositories.UserRepository;
import com.mongodb.DuplicateKeyException;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private PasswordEncoder passwordEncoder;


    public List<User> getAllUsers(){
        return userRepository.findAll();
    }
    public User findById(String id){
        return  userRepository.findById(id).orElse(null);
    }
    public User CreatUser(User user){
        String Password_user=user.getPassword();

        try {
            user.setPassword(passwordEncoder.encode(Password_user));
            return userRepository.save(user);
        } catch (DuplicateKeyException e) {
            throw new RuntimeException("Email já cadastrado");
        }
    }
    public User Update (User user){
        String id = user.getId();
        User response = findById(id);
        if (response != null){
            BeanUtils.copyProperties(user, response );
            try {
                return userRepository.save(response);
            } catch (DuplicateKeyException e) {
                throw new RuntimeException("Email já cadastrado");
            }
        }
        else {
            return null;
        }
    }
    public void deleteUser(String id){
        userRepository.deleteById((id));
    }

}
