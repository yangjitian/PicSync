package com.yudi.cloud.model.dto.user;


import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginDTO implements Serializable {

    private static final long serialVersionUID = 2442735601566953431L;

    private String userAccount;

    private String userPassword;

    private String captcha;

}
