# AWS SSO Setup Guide for Feather Wand

This guide explains how to configure AWS SSO (Single Sign-On) authentication for the Feather Wand JMeter plugin.

## Prerequisites

1. AWS CLI installed and configured
2. Access to an AWS SSO-enabled organization
3. Feather Wand plugin version 1.0.10 or later

## Step 1: Configure AWS SSO Profile

Create or update your AWS configuration file (`~/.aws/config`) with your SSO profile:

```ini
[profile your-sso-profile-name]
sso_start_url = https://your-domain.awsapps.com/start
sso_region = us-east-1
sso_account_id = 123456789012
sso_role_name = YourRoleName
region = us-east-1
output = json
```

## Step 2: Login to AWS SSO

Before using the plugin, authenticate with AWS SSO:

```bash
aws sso login --profile your-sso-profile-name
```

This will open your browser and prompt you to authenticate with your SSO provider.

## Step 3: Configure JMeter Properties

In your `jmeter.properties` or `user.properties` file, set the profile name:

```properties
# AWS Profile Configuration
aws.profile.name=your-sso-profile-name

# AWS Region (should match your profile)
aws.bedrock.region=us-east-1

# Other Bedrock settings
jmeter.ai.service.type=bedrock
bedrock.default.model=anthropic.claude-3-sonnet-20240229-v1:0
```

## Step 4: Verify Configuration

1. Start JMeter
2. Add the Feather Wand plugin to your test plan
3. Try sending a message to verify the connection works

## Troubleshooting

### Common Issues

1. **"SSO session has expired"**
   - Run `aws sso login --profile your-profile-name` again

2. **"Profile not found"**
   - Check that your profile name in the properties file matches the one in `~/.aws/config`
   - Ensure the profile section starts with `[profile profile-name]`

3. **"Access denied"**
   - Verify your SSO role has the necessary Bedrock permissions
   - Check that you're using the correct account ID and role name

### Required AWS Permissions

Your SSO role needs the following permissions for Bedrock:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "bedrock:InvokeModel",
                "bedrock:ListFoundationModels"
            ],
            "Resource": "*"
        }
    ]
}
```

## Session Management

- SSO sessions typically last 8-12 hours
- You'll need to re-authenticate when the session expires
- Consider setting up a longer session duration if supported by your organization

## Security Best Practices

1. Never commit AWS credentials to version control
2. Use the most restrictive IAM permissions possible
3. Regularly rotate your SSO sessions
4. Monitor AWS CloudTrail for unusual API activity

## Support

If you encounter issues with SSO authentication, please check:

1. AWS CLI version compatibility
2. Network connectivity to your SSO provider
3. JMeter logs for detailed error messages

For plugin-specific issues, please report them on the [GitHub repository](https://github.com/qainsights/jmeter-ai).