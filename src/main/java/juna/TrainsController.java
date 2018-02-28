package juna;

import java.util.List;
import java.util.UUID;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class TrainsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrainsController.class);

    @Autowired
    private Juna juna;

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
        if (info == null) {
            info = new UserInfo();
        } else {
            LOGGER.info("Updating user {} from query param", email);
        }
        model.addAttribute("userInfo", info);
        return "trains";
    }

    @PostMapping(name = "/trains", params = "create")
    public String trainsSubmit(@Valid UserInfo userInfo, BindingResult bindingResult) {
        if (bindingResult.hasErrors())
            return "trains";
        
        if (userInfo.getEmail().isEmpty())
            throw new NotAcceptableException();
        
        UserInfo existingInfo = findByEmail(userInfo.getEmail());
        if (existingInfo != null) {
            userInfo.setId(existingInfo.getId());
            LOGGER.info("Updating existing user {} with trainIds {}", userInfo.getEmail(), userInfo.getTrainIds());
        } else {
            LOGGER.info("Creating new user {} with trainIds {}", userInfo.getEmail(), userInfo.getTrainIds());
        }
        
        if (userInfo.getId().isEmpty()) {
            // new user, set pending approval
            userInfo.setId(null);
            String uuid = UUID.randomUUID().toString();
            userInfo.setApprovalPending(uuid);
            LOGGER.info("Setting {} to pend approval with uuid {}", userInfo.getEmail(), uuid);
        }

        repository.save(userInfo);
        
        emailService.sendSimpleMessage(userInfo.getEmail(), "Varmenna sähköpostiosoite " + userInfo.getEmail(), "Varmenna sähköpostiosoitteesi " + userInfo.getEmail() + " klikkaamalla linkkiä: " + juna.getServername() + "/trains/approve?email=" + userInfo.getEmail() + "&uuid=" + userInfo.getApprovalPending());
        
        return "result";
    }

    @GetMapping("/trains/approve")
    public String approveUser(Model model, @RequestParam("uuid") String uuid, @RequestParam("email") String email) {
        UserInfo info = findByEmail(email);
        if (info == null)
            throw new NotAcceptableException();
        if (info.getApprovalPending() != null && info.getApprovalPending().equals(uuid)) {
            LOGGER.info("Approved email {} with uuid {}", email, uuid);
            info.setApprovalPending("");
            repository.save(info);
            
            emailService.sendSimpleMessage(info.getEmail(), "Junien seuranta aloitettu " + info.getTrainIds(), info.getEmail() + " on rekisteröitynyt seuraamaan junia numeroilla " + info.getTrainIds() + ". Lopeta seuranta seuraavasta linkistä: " + juna.getServername() + "/trains/remove?email=" + info.getEmail() + "&trainId=" + info.getTrainIds());
        }
        model.addAttribute("userInfo", info);
        return "ok";
    }
    
    @PostMapping(name = "/trains", params = "delete")
    public String trainsDelete(@Valid UserInfo userInfo, BindingResult bindingResult) {
        
        if (bindingResult.getFieldError("email") != null)
            return "trains";
        
        if (userInfo.getEmail().isEmpty())
            throw new ResourceNotFoundException();
        UserInfo info = findByEmail(userInfo.getEmail());
        if (info == null)
            throw new ResourceNotFoundException();
        repository.delete(info);
        
        LOGGER.info("Removed tracking for user {} for trainIds {}", info.getEmail(), info.getTrainIds());
        emailService.sendSimpleMessage(info.getEmail(), "Seuranta lopetettu junille " + info.getTrainIds(), info.getEmail() + " on lopettanut seuraamasta junia " + info.getTrainIds());
        return "deleted";
    }
    
    @GetMapping("/trains/remove")
    public String trainsRemove(Model model, @RequestParam("email") String email, @RequestParam("trainId") List<String> trainIds) {
        UserInfo info = findByEmail(email);
        if (info == null)
            throw new ResourceNotFoundException();
        List<String> currentTrainIds = info.getTrainIds();
        for (String trainId : trainIds) {
            if (currentTrainIds.remove(trainId)) {
                LOGGER.info("Succesfully removed trainId {} from email {}", trainId, email);
            } else {
                LOGGER.info("Could not remove trainId {} from email {}", trainId, email);
            }
        }
        repository.save(info);
        model.addAttribute("userInfo", info);
        return "ok";
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