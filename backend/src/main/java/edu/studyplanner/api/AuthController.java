package edu.studyplanner.api;

import edu.studyplanner.domain.Student;
import edu.studyplanner.repository.StudentRepository;
import edu.studyplanner.repository.PreferenceRepository;
import edu.studyplanner.domain.StudentPreference;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import static edu.studyplanner.api.ApiModels.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final StudentRepository students; private final PreferenceRepository preferences; private final PasswordEncoder encoder; private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    public AuthController(StudentRepository students, PreferenceRepository preferences, PasswordEncoder encoder, AuthenticationManager authenticationManager) {
        this.students = students; this.preferences = preferences; this.encoder = encoder; this.authenticationManager = authenticationManager;
    }
    @GetMapping("/csrf") public Map<String, Boolean> csrf() { return Map.of("ready", true); }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http, HttpServletResponse response) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (students.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be no more than 72 UTF-8 bytes");
        Student student;
        try {
            student = students.saveAndFlush(new Student(request.name().trim(), email, encoder.encode(request.password()), false));
        } catch (DataIntegrityViolationException ex) {
            // The unique constraint is the source of truth if two requests race past the pre-check.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        preferences.save(new StudentPreference(student.id));
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        establishSession(authentication, http, response);
        return new AuthResponse(dto(student));
    }
    @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        Authentication authentication;
        try { authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(Locale.ROOT), request.password())); }
        catch (AuthenticationException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"); }
        establishSession(authentication, http, response);
        Student student = students.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        return new AuthResponse(dto(student));
    }
    private void establishSession(Authentication authentication, HttpServletRequest http, HttpServletResponse response) {
        // The SPA starts without a session. Create one before rotating its identifier
        // so a pre-authentication session cannot be reused after successful login.
        http.getSession(true);
        sessionAuthenticationStrategy.onAuthentication(authentication, http, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, http, response);
    }
    @PostMapping("/logout") public MessageDto logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext(); HttpSession session = request.getSession(false); if (session != null) session.invalidate();
        return new MessageDto("Signed out");
    }
    @GetMapping("/me") public AuthResponse me(@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        return new AuthResponse(dto(students.findByEmailIgnoreCase(user.getUsername()).orElseThrow()));
    }
    private StudentDto dto(Student s) { return new StudentDto(s.id, s.name, s.email, s.demo); }
}

