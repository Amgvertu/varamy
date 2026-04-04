package info.prorabka.varamy.service;

import info.prorabka.varamy.dto.request.ChangePasswordRequest;
import info.prorabka.varamy.dto.response.UserResponse;
import info.prorabka.varamy.entity.User;
import info.prorabka.varamy.exception.BadRequestException;
import info.prorabka.varamy.exception.ResourceNotFoundException;
import info.prorabka.varamy.mapper.UserMapper;
import info.prorabka.varamy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    }

    public UserResponse getUserResponseById(UUID id) {
        return userMapper.toResponse(getUserById(id));
    }

    public User getCurrentUser(UUID userId) {
        return getUserById(userId);
    }

    public UserResponse getCurrentUserResponse(UUID userId) {
        return userMapper.toResponse(getCurrentUser(userId));
    }

    public Page<UserResponse> getUsers(String phone, User.UserRole role, User.UserStatus status, Pageable pageable) {
        return userRepository.findWithFilters(phone, role, status, pageable)
                .map(userMapper::toResponse);
    }

    @Transactional
    public UserResponse updateUser(UUID id, String phone, User.UserRole role, User.UserStatus status, String password) {
        User user = getUserById(id);

        if (phone != null && !phone.equals(user.getPhone())) {
            if (userRepository.existsByPhone(phone)) {
                throw new BadRequestException("Телефон уже используется");
            }
            user.setPhone(phone);
        }

        if (role != null) {
            user.setRole(role);
        }

        if (status != null) {
            user.setStatus(status);
        }

        if (password != null) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void deleteUser(UUID id, boolean hardDelete) {
        User user = getUserById(id);

        if (hardDelete) {
            userRepository.delete(user);
        } else {
            user.setStatus(User.UserStatus.BLOCKED);
            userRepository.save(user);
        }
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Неверный старый пароль");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void changePhone(UUID userId, String newPhone, String password) {
        User user = getUserById(userId);

        // Если передан пароль, проверяем его
        if (password != null && !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("Неверный пароль");
        }

        if (userRepository.existsByPhone(newPhone)) {
            throw new BadRequestException("Телефон уже используется");
        }

        user.setPhone(newPhone);
        userRepository.save(user);
    }

    public boolean isPhoneExists(String phone) {
        return userRepository.existsByPhone(phone);
    }

    // UserService.java
    public List<User> findUsersForNewAdNotification(Long cityId, Integer type, Integer subType) {
        return userRepository.findUsersForNewAdNotification(cityId, type, subType);
    }
}
