package ar.edu.uade.toto.toto_backend.validation;

import ar.edu.uade.toto.toto_backend.dto.auth.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ElderlyCredentialsValidator implements ConstraintValidator<ValidateElderlyCredentials, RegisterRequest> {

    @Override
    public void initialize(ValidateElderlyCredentials constraintAnnotation) {
    }

    @Override
    public boolean isValid(RegisterRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return false;
        }

        String role = request.getRole();
        
        if ("CAREGIVER".equals(role)) {
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("El email es obligatorio para CAREGIVER")
                        .addPropertyNode("email")
                        .addConstraintViolation();
                return false;
            }
            
            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("La contraseña es obligatoria para CAREGIVER")
                        .addPropertyNode("password")
                        .addConstraintViolation();
                return false;
            }
            
            if (!isValidEmail(request.getEmail())) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Formato de email inválido")
                        .addPropertyNode("email")
                        .addConstraintViolation();
                return false;
            }
            
            if (request.getPassword().length() < 6) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("La contraseña debe tener al menos 6 caracteres")
                        .addPropertyNode("password")
                        .addConstraintViolation();
                return false;
            }
        }
        
        return true;
    }
    
    private boolean isValidEmail(String email) {
        if (email == null) return false;
        return email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    }
}