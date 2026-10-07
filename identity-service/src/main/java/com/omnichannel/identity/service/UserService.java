package com.omnichannel.identity.service;

import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;
import com.omnichannel.identity.dto.UserDtos.AddressRequest;
import com.omnichannel.identity.dto.UserDtos.AddressResponse;
import com.omnichannel.identity.dto.UserDtos.UserResponse;
import com.omnichannel.identity.entity.Address;
import com.omnichannel.identity.entity.Role;
import com.omnichannel.identity.entity.User;
import com.omnichannel.identity.repository.AddressRepository;
import com.omnichannel.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final AddressRepository addresses;

    public UserService(UserRepository users, AddressRepository addresses) {
        this.users = users;
        this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return UserResponse.of(find(userId));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(UUID userId) {
        return addresses.findByUserId(userId).stream().map(AddressResponse::of).toList();
    }

    @Transactional
    public AddressResponse addAddress(UUID userId, AddressRequest req) {
        List<Address> existing = addresses.findByUserId(userId);
        boolean makeDefault = req.defaultAddress() || existing.isEmpty();
        if (makeDefault) {
            existing.forEach(a -> a.setDefaultAddress(false));
        }
        Address saved = addresses.save(new Address(userId, req.receiverName(), req.phone(), req.line1(),
                req.ward(), req.district(), req.city(), makeDefault));
        return AddressResponse.of(saved);
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        Address a = addresses.findById(addressId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Address not found"));
        addresses.delete(a);
    }

    @Transactional
    public UserResponse updateRoles(UUID targetUserId, Set<Role> roles) {
        User u = find(targetUserId);
        u.getRoles().clear();
        u.getRoles().addAll(roles);
        return UserResponse.of(u);
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found"));
    }
}
