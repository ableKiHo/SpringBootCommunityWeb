package com.web.config;

import com.web.domain.enums.SocialType;
import com.web.oauth.ClientResources;
import com.web.oauth.UserTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.filter.OAuth2ClientAuthenticationProcessingFilter;
import org.springframework.security.oauth2.client.filter.OAuth2ClientContextFilter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.filter.CompositeFilter;

import javax.servlet.Filter;
import java.util.ArrayList;
import java.util.List;

import static com.web.domain.enums.SocialType.KAKAO;

@Configuration
@EnableWebSecurity
@EnableOAuth2Client
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private OAuth2ClientContext oAuth2ClientContext;

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        /*authorizeRequests() : 인증 메커니즘을 요청한 HttpServletRequest 기반으로 설정
            - antMatchers() : 요청 패턴을 리스트 형식으로 설정합니다.
            - permitAll() : 설정한 리퀘스트 패턴을 누구나 접근할 수 있도록 허용
            - anyRequest() : 설정한 요청 이외의 리퀘스트 요청을 표현
            - authenticated() : 해당 요청은 인증된 사용자만 사용 가능
          headers() : 응답에 해당하는 header를 설정. 설정하지 않으면 디폴트
            - frameOptions().disable() : XFrameOptionsHeaderWriter의 최적화 설정을 허용하지 않음.
          authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                    : 인증의 진입 지점, 인증되지 않은 사용자가 허용되지 않은 경로로 리퀘스트 요청시 이동주소 지정
          formLogin().successForwardUrl("/board/list") : 로그인에 성공하면 설정된 경로로 포워딩
          logout() : 로그아웃에 대한 설정
            - logoutUrl() : 로그아웃이 수행될 URL
            - logoutSuccessUrl() : 로그아웃이 성공했을때 포워딩될 URL
            - deleteCookies() : 로그아웃 성공시 삭제될 쿠키
            - invalidateHttpSession() : 로그아웃 성공시 설정된 세션의 무효화
          addFilterBefore(filter, beforeFilter) : 첫번째 인자보다 먼저 시작될 필터 등록
            - addFilterBefore(filter, CsrfFilter.class) : 문자 인코딩 필터(filter)보다 CsrfFilter를 먼저 실행
        * */

        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        http.authorizeRequests()
                    .antMatchers("/", "/login/**", "/css/**", "/images/**"
                        , "/js/**", "/console/**").permitAll()
                    .anyRequest().authenticated()
                .and().headers().frameOptions().disable()
                .and().exceptionHandling().authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                .and().formLogin().successForwardUrl("/board/list")
                .and().logout().logoutUrl("/logout").logoutSuccessUrl("/").deleteCookies("JSESSIONID").invalidateHttpSession(true)
                .and().addFilterBefore(filter, CsrfFilter.class)
                        .addFilterBefore(oauth2Filter(), BasicAuthenticationFilter.class).csrf().disable();
    }

    @Bean
    public FilterRegistrationBean oauth2ClientFilterRegistration(
            OAuth2ClientContextFilter filter) {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(filter);
        registration.setOrder(-100); // 스프링 시큐리티 필터가 실행되기 전에 충분히 낮은 순서로 필터 등록
        return registration;
    }

    private Filter oauth2Filter() {
        CompositeFilter filter = new CompositeFilter();
        List<Filter> filters = new ArrayList<>();
        filters.add(oauth2Filter(kakao(), "/login/kakao",KAKAO));
        filter.setFilters(filters);
        return filter;
    }

    private Filter oauth2Filter(ClientResources client, String path, SocialType socialType) {
        //인증이 수행될 경로를 넣어 OAuth2 클라이언트용 인증 처리 필터를 생성
        OAuth2ClientAuthenticationProcessingFilter filter = new OAuth2ClientAuthenticationProcessingFilter(path);
        //권한 서버와의 통신을 위해 OAuth2RestTemplate을 생성.
        OAuth2RestTemplate template = new OAuth2RestTemplate(client.getClient(), oAuth2ClientContext);
        filter.setRestTemplate(template);
        //User의 권한을 최적화해서 생성하고자 UserInfoTokenServices를 상속받은 UserTokenService를 생성
        filter.setTokenServices(new UserTokenService(client, socialType));
        //인증이 성공적으로 이루어지면 필터에 리다이렉트될 URL을 설정
        filter.setAuthenticationSuccessHandler((request, response, authentication)
                -> response.sendRedirect("/" + socialType.getValue() + "/complate"));
        //인증이 실패하면 필터에 리다이렉트 될 URL을 설정
        filter.setAuthenticationFailureHandler((request, response, exception)
                -> response.sendRedirect("/error"));
        return filter;
    }


    @Bean
    @ConfigurationProperties("kakao")
    public ClientResources kakao() {
        return new ClientResources();
    }
}
