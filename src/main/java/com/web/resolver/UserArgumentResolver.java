package com.web.resolver;

import com.web.annotation.SocialUser;
import com.web.domain.User;
import com.web.domain.enums.SocialType;
import com.web.repository.UserRepository;
import org.apache.tomcat.jni.Local;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.web.domain.enums.SocialType.*;

@Component
public class UserArgumentResolver implements HandlerMethodArgumentResolver {

    private UserRepository userRepository;

    public UserArgumentResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(SocialUser.class) != null &&
                parameter.getParameterType().equals(User.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer
            , NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

        HttpSession session = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getSession();
        User user  = (User) session.getAttribute("user");
        return getUser(user, session);
    }

    //인증된 User 객체를 만든다.
    private User getUser(User user, HttpSession session) {
        if(user == null) {
            try {
                OAuth2Authentication authentication = (OAuth2Authentication) SecurityContextHolder.getContext().getAuthentication();
                Map<String, String> map = (HashMap<String, String>) authentication.getUserAuthentication().getDetails();
                User convertUser = convertUser(String.valueOf(authentication.getAuthorities().toArray()[0]), map);

                user = userRepository.findByEmail(convertUser.getEmail());
                if(user == null) {
                    user = userRepository.save(convertUser);
                }

                setRoleIfNotSame(user, authentication, map);
                session.setAttribute("user", user);
            } catch (ClassCastException e) {
                return user;
            }
        }
        return user;
    }


    //사용자의 인증된 소셜 미디어 타입에 따라 빌더를 사용하여 User객체를 만들어주는 가교역할
    private User convertUser(String authority, Map<String, String> map) {
        if(FACEBOOK.isEquals(authority)) return getModernUser(FACEBOOK, map);
        else if(GOOGLE.isEquals(authority)) return getModernUser(GOOGLE, map);
        else if(KAKAO.isEquals(authority)) return getKaKaoUser(map);
        return null;
    }


    //페이스북이나 구글과 같이 공통되는 명명규칙을 가직 그룹을 User객체로 매핑
    private User getModernUser(SocialType socialType, Map<String, String> map) {
        return User.builder()
            .name(map.get("name"))
            .email(map.get("email"))
            .principal(map.get("id"))
            .socialType(socialType)
            .createdDate(LocalDateTime.now())
            .build();
    }

    //(키의 네이밍값이 타 소셜 미디어와 다른)카카오 회월용
    private User getKaKaoUser(Map<String, String> map) {
        HashMap<String, String> propertyMap = (HashMap<String, String>) (Object) map.get("properties");
        return User.builder()
                .name(map.get("name"))
                .email(map.get("email"))
                .principal(map.get("id"))
                .socialType(KAKAO)
                .createdDate(LocalDateTime.now())
                .build();
    }

    //인증된 authentication이 권한을 갖고 있는지 체크하는 용도. 마약 저장된 user 권한이 없으면 SecurityContextHolder를 사용하여권한 저장
    private void setRoleIfNotSame(User user, OAuth2Authentication authentication, Map<String, String> map) {
        if(!authentication.getAuthorities().contains(new SimpleGrantedAuthority(user.getSocialType().getRoleType()))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(map, "N/A",
                    AuthorityUtils.createAuthorityList(user.getSocialType().getRoleType())));
        }
    }

}
