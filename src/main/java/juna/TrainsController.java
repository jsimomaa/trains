package juna;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class TrainsController {

    @Autowired
    private UserInfoRepository repository;

    @Autowired
    private EmailService emailService;
    
    @GetMapping("/")
    public String index() {
        return "index";
    }
    
    @GetMapping("/trains")
    public String trainsForm(Model model, @RequestParam(name="email", required=false) String email) {
        UserInfo info = findByEmail(email);
        if (info == null)
            info = new UserInfo();
        model.addAttribute("userInfo", info);
        return "trains";
    }

    @PostMapping(name = "/trains", params = "create")
    public String trainsSubmit(@ModelAttribute UserInfo userInfo) {
        if (userInfo.getEmail().isEmpty())
            throw new NotAcceptableException();
        
        if (userInfo.getId().isEmpty())
            userInfo.setId(null);
        
        UserInfo existingInfo = findByEmail(userInfo.getEmail());
        if (existingInfo != null)
            userInfo.setId(existingInfo.getId());

        repository.save(userInfo);
        
        emailService.sendSimpleMessage(userInfo.getEmail(), "Registered for trains " + userInfo.getTrainIds(), userInfo.getEmail() + " has registered to track trains with ids " + userInfo.getTrainIds() + ". To stop tracking fill in the email to the form and click 'Delete'.");
        
        return "result";
    }

    @PostMapping(name = "/trains", params = "delete")
    public String trainsDelete(@ModelAttribute UserInfo userInfo) {
        if (userInfo.getEmail().isEmpty())
            throw new ResourceNotFoundException();
        UserInfo info = findByEmail(userInfo.getEmail());
        if (info == null)
            throw new ResourceNotFoundException();
        repository.delete(info);
        
        emailService.sendSimpleMessage(info.getEmail(), "Removed tracking for trains " + info.getTrainIds(), info.getEmail() + " has deregistered to track trains with ids " + info.getTrainIds());
        return "deleted";
    }
    
    private UserInfo findByEmail(String email) {
        if (email != null) {
            for (UserInfo possibleInfo : repository.findAll()) {
                if (possibleInfo != null && email.equals(possibleInfo.getEmail())) {
                    return possibleInfo;
                }
            }
        }
        return null;
    }

    @ResponseStatus(value = HttpStatus.NOT_ACCEPTABLE)
    private static class NotAcceptableException extends RuntimeException {
        private static final long serialVersionUID = -2575617832431722829L;
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    private static class ResourceNotFoundException extends RuntimeException {
        private static final long serialVersionUID = -4089156278770933855L;
    }
}