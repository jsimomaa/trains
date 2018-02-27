package juna;

import java.util.List;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.repository.CrudRepository;

@EnableScan
public interface UserInfoRepository extends CrudRepository<UserInfo, String> {

    List<UserInfo> findById(String email);
}