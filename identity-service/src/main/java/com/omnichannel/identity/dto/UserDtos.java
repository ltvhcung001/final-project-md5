package com.omnichannel.identity.dto;

import com.omnichannel.identity.entity.Address;
import com.omnichannel.identity.entity.Role;
import com.omnichannel.identity.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(UUID id, String email, String fullName, String phone, Set<Role> roles) {
        public static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRoles());
        }
    }

    public record AddressRequest(
            @NotBlank String receiverName,
            @NotBlank String phone,
            @NotBlank String line1,
            String ward,
            String district,
            @NotBlank String city,
            boolean defaultAddress) {
    }

    public record AddressResponse(UUID id, String receiverName, String phone, String line1, String ward,
                                  String district, String city, boolean defaultAddress) {
        public static AddressResponse of(Address a) {
            return new AddressResponse(a.getId(), a.getReceiverName(), a.getPhone(), a.getLine1(), a.getWard(),
                    a.getDistrict(), a.getCity(), a.isDefaultAddress());
        }
    }

    public record UpdateRolesRequest(@NotEmpty Set<Role> roles) {
    }
}
