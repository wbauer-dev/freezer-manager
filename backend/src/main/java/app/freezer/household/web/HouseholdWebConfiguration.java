package app.freezer.household.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import app.freezer.household.CurrentUser;
import app.freezer.household.HouseholdAccess;
import app.freezer.household.HouseholdId;
import app.freezer.household.HouseholdMember;
import app.freezer.household.HouseholdNotFoundException;
import app.freezer.household.UserId;

/**
 * Lets controllers of all modules declare {@link CurrentUser} and {@link HouseholdMember} as method parameters.
 */
@Configuration(proxyBeanMethods = false)
class HouseholdWebConfiguration implements WebMvcConfigurer {

	static final String HOUSEHOLD_ID_VARIABLE = "householdId";

	private final HouseholdAccess access;

	HouseholdWebConfiguration(HouseholdAccess access) {
		this.access = access;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new CurrentUserResolver());
		resolvers.add(new HouseholdMemberResolver(access));
	}

	static CurrentUser currentUser() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			throw new IllegalStateException("No authenticated user with an access token");
		}
		var displayName = firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username"),
				jwt.getSubject());
		return new CurrentUser(UserId.of(jwt.getSubject()), displayName);
	}

	private static String firstNonBlank(String... values) {
		for (var value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		throw new IllegalStateException("Access token contains no usable user name");
	}

	static class CurrentUserResolver implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return CurrentUser.class.equals(parameter.getParameterType());
		}

		@Override
		public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
				NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
			return currentUser();
		}
	}

	/**
	 * Resolves the household from the {@code {householdId}} path variable and checks that the current user is a
	 * member.
	 */
	static class HouseholdMemberResolver implements HandlerMethodArgumentResolver {

		private final HouseholdAccess access;

		HouseholdMemberResolver(HouseholdAccess access) {
			this.access = access;
		}

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return HouseholdMember.class.equals(parameter.getParameterType());
		}

		@Override
		public HouseholdMember resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
				NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
			@SuppressWarnings("unchecked")
			var variables = (Map<String, String>) webRequest.getAttribute(
					HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
			var value = variables == null ? null : variables.get(HOUSEHOLD_ID_VARIABLE);
			if (value == null) {
				throw new IllegalStateException(
						"HouseholdMember parameters need a {" + HOUSEHOLD_ID_VARIABLE + "} path variable");
			}
			return access.requireMember(parseHouseholdId(value), currentUser());
		}

		private static HouseholdId parseHouseholdId(String value) {
			try {
				return HouseholdId.of(UUID.fromString(value));
			}
			catch (IllegalArgumentException e) {
				throw new HouseholdNotFoundException(null, e);
			}
		}
	}

}
